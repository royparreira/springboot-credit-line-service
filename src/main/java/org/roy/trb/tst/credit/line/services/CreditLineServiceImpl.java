package org.roy.trb.tst.credit.line.services;

import static org.roy.trb.tst.credit.line.constants.BusinessRulesConstants.MAX_NUMBER_OF_FAILED_ATTEMPTS;
import static org.roy.trb.tst.credit.line.constants.Messages.SALES_AGENT_MSG;
import static org.roy.trb.tst.credit.line.enums.CreditLineStatus.REJECTED;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.roy.trb.tst.credit.line.entities.CreditLineRequestRecord;
import org.roy.trb.tst.credit.line.enums.CreditLineStatus;
import org.roy.trb.tst.credit.line.enums.FoundingType;
import org.roy.trb.tst.credit.line.exceptions.RejectedCreditLineException;
import org.roy.trb.tst.credit.line.models.daos.CreditLineRequestRecordDao;
import org.roy.trb.tst.credit.line.models.dtos.RequesterFinancialData;
import org.roy.trb.tst.credit.line.models.requests.PostRequestCreditLineRequestBody;
import org.roy.trb.tst.credit.line.models.responses.PostRequestCreditLineResponseBody;
import org.roy.trb.tst.credit.line.repositories.CreditLineRequestRepository;
import org.roy.trb.tst.credit.line.services.mappers.CreditLineRequestMapper;
import org.roy.trb.tst.credit.line.services.strategies.credit.status.CreditRequestStatusStrategy;
import org.roy.trb.tst.credit.line.services.strategies.founding.type.FoundingTypeStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CreditLineServiceImpl implements CreditLineService {

  // Dependency Injection
  private final CreditLineRequestMapper mapper;
  private final CreditLineRequestRepository creditLineRequestsRepository;

  // Strategies
  private FoundingTypeStrategy foundingTypeStrategy;
  private CreditRequestStatusStrategy creditRequestStatusStrategy;

  /** {@inheritDoc} */
  @Override
  public PostRequestCreditLineResponseBody requestCreditLine(
      UUID customerId,
      PostRequestCreditLineRequestBody postRequestCreditLineRequestBody,
      FoundingType foundingType) {

    // "requestedDate" attribute must comply with real data
    ZonedDateTime requestedDate = Instant.now().atZone(ZoneOffset.UTC);
    postRequestCreditLineRequestBody.setRequestedDate(requestedDate);

    var requesterFinancialData =
        mapper.mapToRequesterFinancialData(postRequestCreditLineRequestBody);

    foundingTypeStrategy = FoundingTypeStrategy.getFoundingTypeStrategy(foundingType);

    Optional<CreditLineRequestRecord> optionalCreditLineRequest =
        creditLineRequestsRepository.findById(customerId);

    if (optionalCreditLineRequest.isPresent()) {

      CreditLineRequestRecordDao existentCreditLineResponse =
          processExistentCreditLineResponse(
              requesterFinancialData, requestedDate, optionalCreditLineRequest.get());

      return getValidateCreditApiResponse(existentCreditLineResponse);
    }

    var creditLineStatusResponse =
        processNewCreditLineResponse(customerId, requesterFinancialData, requestedDate);

    return getValidateCreditApiResponse(creditLineStatusResponse);
  }

  public PostRequestCreditLineResponseBody requestCreditLineV2(
      UUID customerId,
      PostRequestCreditLineRequestBody postRequestCreditLineRequestBody,
      FoundingType foundingType) {

    // "requestedDate" attribute must comply with real data
    ZonedDateTime requestedDate = Instant.now().atZone(ZoneOffset.UTC);
    postRequestCreditLineRequestBody.setRequestedDate(requestedDate);

    var requesterFinancialData =
        mapper.mapToRequesterFinancialData(postRequestCreditLineRequestBody);

    Optional<CreditLineRequestRecord> optionalCreditLineRequest =
        creditLineRequestsRepository.findById(customerId);

    if (optionalCreditLineRequest.isPresent()) {

      CreditLineRequestRecordDao existentCreditLineResponse =
          processExistentCreditLineResponse(
              requesterFinancialData, requestedDate, optionalCreditLineRequest.get());

      return getValidateCreditApiResponse(existentCreditLineResponse);
    }

    var creditLineStatusResponse =
        processNewCreditLineResponse(customerId, requesterFinancialData, requestedDate);

    return getValidateCreditApiResponse(creditLineStatusResponse);
  }

  /** {@inheritDoc} */
  @Override
  public CreditLineStatus getCustomerCreditLineStatus(UUID customerId) {

    CreditLineRequestRecord entity =
        creditLineRequestsRepository
            .findById(customerId)
            .orElse(CreditLineRequestRecord.builder().creditLineStatus(REJECTED.name()).build());

    return CreditLineStatus.valueOf(entity.getCreditLineStatus());
  }

  private PostRequestCreditLineResponseBody getValidateCreditApiResponse(
      CreditLineRequestRecordDao creditLineRequestRecordDao) {

    if (creditLineRequestRecordDao.getCreditLineStatus().equals(REJECTED)) {
      throw new RejectedCreditLineException();
    }

    return mapper.mapToRequestCreditLineResponseBody(creditLineRequestRecordDao);
  }

  private CreditLineRequestRecordDao processNewCreditLineResponse(
      UUID customerId, RequesterFinancialData requesterFinancialData, ZonedDateTime requestedDate) {

    BigDecimal approvedCredit = foundingTypeStrategy.getCreditLine(requesterFinancialData);

    var creditLineStatusResponse =
        CreditLineRequestRecordDao.builder()
            .customerId(customerId)
            .acceptedCreditLine(approvedCredit)
            .attempts(0)
            .build();

    saveOrUpdateCreditLineResponse(creditLineStatusResponse, approvedCredit, requestedDate);

    return creditLineStatusResponse;
  }

  private CreditLineRequestRecordDao processExistentCreditLineResponse(
      RequesterFinancialData requesterFinancialData,
      ZonedDateTime requestedDate,
      CreditLineRequestRecord creditLineRequest) {
    var existentCreditLineResponse = mapper.mapToCreditLineRequestRecordDao(creditLineRequest);

    if (REJECTED.equals(existentCreditLineResponse.getCreditLineStatus())) {

      if (existentCreditLineResponse.getAttempts() > MAX_NUMBER_OF_FAILED_ATTEMPTS) {
        throw new RejectedCreditLineException(SALES_AGENT_MSG);
      }

      BigDecimal approvedCredit = foundingTypeStrategy.getCreditLine(requesterFinancialData);

      saveOrUpdateCreditLineResponse(existentCreditLineResponse, approvedCredit, requestedDate);
    }

    saveOrUpdateCreditLineResponse(
        existentCreditLineResponse,
        existentCreditLineResponse.getAcceptedCreditLine(),
        requestedDate);

    return existentCreditLineResponse;
  }

  /**
   * Persist on DB the most up-to-date info regarding credit line request
   *
   * @param creditLineRequestRecordDao request data
   * @param approvedCredit approved credit for the request. Zero if request was rejected
   * @param requestedDate date that the request was made.
   */
  private void saveOrUpdateCreditLineResponse(
      CreditLineRequestRecordDao creditLineRequestRecordDao,
      BigDecimal approvedCredit,
      ZonedDateTime requestedDate) {

    Integer currentAttempts = creditLineRequestRecordDao.getAttempts();

    var requestStatus = getProcessedCreditLineRequestStatus(approvedCredit);

    creditLineRequestRecordDao.setAcceptedCreditLine(approvedCredit);
    creditLineRequestRecordDao.setCreditLineStatus(requestStatus);
    creditLineRequestRecordDao.setRequestedDate(requestedDate);
    creditLineRequestRecordDao.setAttempts(++currentAttempts);

    creditLineRequestsRepository.save(
        mapper.mapToCreditLineRequestEntity(creditLineRequestRecordDao));
  }

  /**
   * Get the credit line status based on the approved credit
   *
   * @param approvedCredit approved credit after process the credit line request
   * @return costumer credit line status
   */
  private CreditLineStatus getProcessedCreditLineRequestStatus(BigDecimal approvedCredit) {
    return approvedCredit.equals(BigDecimal.ZERO) ? REJECTED : CreditLineStatus.ACCEPTED;
  }
}
