package org.roy.trb.tst.credit.line.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.roy.trb.tst.credit.line.constants.Descriptions.SALES_AGENT_MSG;
import static org.roy.trb.tst.credit.line.fixture.CreditLineEntityFixture.mockAlreadyAcceptedRequest;
import static org.roy.trb.tst.credit.line.fixture.CreditLineEntityFixture.mockAlreadyRejectedRequest;
import static org.roy.trb.tst.credit.line.fixture.CreditLineRequestFixture.CASH_BALANCE_RATIO;
import static org.roy.trb.tst.credit.line.fixture.CreditLineRequestFixture.MOCKED_UUID_CUSTOMER_ID;
import static org.roy.trb.tst.credit.line.fixture.CreditLineRequestFixture.MONTHLY_REVENUE_RATIO;
import static org.roy.trb.tst.credit.line.fixture.CreditLineRequestFixture.mockSmeAcceptableRequest;
import static org.roy.trb.tst.credit.line.fixture.CreditLineRequestFixture.mockSmeRejectableRequest;
import static org.roy.trb.tst.credit.line.fixture.CreditLineRequestFixture.mockStartURejectableRequest;
import static org.roy.trb.tst.credit.line.fixture.CreditLineRequestFixture.mockStartUpAcceptableRequest;
import static org.roy.trb.tst.credit.line.services.CreditLineServiceImpl.MAX_NUMBER_OF_FAILED_ATTEMPTS;
import static org.roy.trb.tst.credit.line.utils.MathUtils.roundFloatTwoPlaces;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.roy.trb.tst.credit.line.enums.CreditLineStatus;
import org.roy.trb.tst.credit.line.enums.FoundingType;
import org.roy.trb.tst.credit.line.exceptions.NotFoundException;
import org.roy.trb.tst.credit.line.exceptions.RejectedCreditLineException;
import org.roy.trb.tst.credit.line.models.requests.CreditLineRequest;
import org.roy.trb.tst.credit.line.models.responses.CreditLineApiResponse;
import org.roy.trb.tst.credit.line.repositories.CreditLineRequestRepository;
import org.roy.trb.tst.credit.line.services.mappers.CreditLineRequestMapper;

@ExtendWith(MockitoExtension.class)
class CreditLineServiceTest {

  @InjectMocks private CreditLineServiceImpl creditLineService;
  @Spy private CreditLineRequestMapper mapper = Mappers.getMapper(CreditLineRequestMapper.class);
  @Mock private CreditLineRequestRepository creditLineRequestsRepository;

  private static Stream<Arguments> getAcceptableCreditLineRequests() {

    return Stream.of(
        Arguments.of(mockSmeAcceptableRequest(), FoundingType.SME),
        Arguments.of(mockStartUpAcceptableRequest(), FoundingType.STARTUP));
  }

  private static Stream<Arguments> getRejectableCreditLineRequests() {

    return Stream.of(
        Arguments.of(mockSmeRejectableRequest(), FoundingType.SME),
        Arguments.of(mockStartURejectableRequest(), FoundingType.STARTUP));
  }

  private static Stream<Arguments> getMixCreditLineRequests() {
    return Stream.concat(getAcceptableCreditLineRequests(), getRejectableCreditLineRequests());
  }

  @BeforeEach
  void setUpIndividualMocks() {
    creditLineService.setCashBalanceRatio(CASH_BALANCE_RATIO);
    creditLineService.setMonthlyRevenueRatio(MONTHLY_REVENUE_RATIO);
  }

  @ParameterizedTest
  @MethodSource("getAcceptableCreditLineRequests")
  void shouldAcceptNewCreditLineRequest(
      CreditLineRequest creditLineRequest, FoundingType foundingType) {

    // given
    BigDecimal expectedAcceptedCreditLine =
        roundFloatTwoPlaces(creditLineRequest.getRequestedCreditLine());

    lenient()
        .when(creditLineRequestsRepository.findById(any(UUID.class)))
        .thenReturn(Optional.empty());

    // act
    CreditLineApiResponse acceptedCreditLine =
        creditLineService.validateCreditLine(
            MOCKED_UUID_CUSTOMER_ID, creditLineRequest, foundingType);

    // expect
    assertEquals(expectedAcceptedCreditLine, acceptedCreditLine.getAcceptedCreditLine());
    assertEquals(CreditLineStatus.ACCEPTED, acceptedCreditLine.getCreditLineStatus());
  }

  @ParameterizedTest
  @MethodSource("getRejectableCreditLineRequests")
  void shouldRejectNewCreditLineRequest(
      CreditLineRequest creditLineRequest, FoundingType foundingType) {

    // given
    lenient()
        .when(creditLineRequestsRepository.findById(any(UUID.class)))
        .thenReturn(Optional.empty());

    // act and expect
    assertThrows(
        RejectedCreditLineException.class,
        () ->
            creditLineService.validateCreditLine(
                MOCKED_UUID_CUSTOMER_ID, creditLineRequest, foundingType));
  }

  // Accept already accepted
  @ParameterizedTest
  @MethodSource("getMixCreditLineRequests")
  void shouldAcceptAlreadyAcceptedCreditLineRequestWithSameValuesRegardlessTheInput(
      CreditLineRequest creditLineRequest, FoundingType foundingType) {
    // given
    lenient()
        .when(creditLineRequestsRepository.findById(any(UUID.class)))
        .thenReturn(mockAlreadyAcceptedRequest());

    CreditLineApiResponse creditLineApiResponse =
        creditLineService.validateCreditLine(
            MOCKED_UUID_CUSTOMER_ID, creditLineRequest, foundingType);

    assertEquals(CreditLineStatus.ACCEPTED, creditLineApiResponse.getCreditLineStatus());
    assertEquals(new BigDecimal("10000.00"), creditLineApiResponse.getAcceptedCreditLine());
  }

  // Accept already rejected - less than more than maximum allowed
  @ParameterizedTest
  @MethodSource("getAcceptableCreditLineRequests")
  void shouldAcceptAlreadyRejectedCreditLineRequestIfNotRejectedMoreThanMaximumAllowed(
      CreditLineRequest creditLineRequest, FoundingType foundingType) {

    lenient()
        .when(creditLineRequestsRepository.findById(any(UUID.class)))
        .thenReturn(mockAlreadyRejectedRequest(MAX_NUMBER_OF_FAILED_ATTEMPTS));

    CreditLineApiResponse creditLineApiResponse =
        creditLineService.validateCreditLine(
            MOCKED_UUID_CUSTOMER_ID, creditLineRequest, foundingType);

    assertEquals(CreditLineStatus.ACCEPTED, creditLineApiResponse.getCreditLineStatus());
  }

  // Reject already rejected - more than maximum allowed
  @ParameterizedTest
  @MethodSource("getMixCreditLineRequests")
  void shouldRejectAlreadyRejectedCreditLineRequestIfRejectedMoreThanMaximumAllowed(
      CreditLineRequest creditLineRequest, FoundingType foundingType) {

    // given
    lenient()
        .when(creditLineRequestsRepository.findById(any(UUID.class)))
        .thenReturn(mockAlreadyRejectedRequest(MAX_NUMBER_OF_FAILED_ATTEMPTS + 1));

    // act
    RejectedCreditLineException exception =
        assertThrows(
            RejectedCreditLineException.class,
            () ->
                creditLineService.validateCreditLine(
                    MOCKED_UUID_CUSTOMER_ID, creditLineRequest, foundingType));

    // expect
    assertEquals(SALES_AGENT_MSG, exception.getCustomMessage());
  }

  // Reject already rejected less than maximum allowed
  @ParameterizedTest
  @MethodSource("getRejectableCreditLineRequests")
  void shouldRejectAlreadyRejectedCreditLineRequestWithCustomMessageMoreThanThreeFailures(
      CreditLineRequest creditLineRequest, FoundingType foundingType) {

    // given
    lenient()
        .when(creditLineRequestsRepository.findById(any(UUID.class)))
        .thenReturn(mockAlreadyRejectedRequest(MAX_NUMBER_OF_FAILED_ATTEMPTS));

    // act
    RejectedCreditLineException exception =
        assertThrows(
            RejectedCreditLineException.class,
            () ->
                creditLineService.validateCreditLine(
                    MOCKED_UUID_CUSTOMER_ID, creditLineRequest, foundingType));

    // expect
    assertTrue(exception.getCustomMessage().isEmpty());
  }

  @Test
  void shouldSuccessfullyGetExistentCreditLineRecord() {
    // given
    when(creditLineRequestsRepository.findById(any(UUID.class)))
        .thenReturn(mockAlreadyRejectedRequest(MAX_NUMBER_OF_FAILED_ATTEMPTS));

    // act
    creditLineService.getCustomerCreditLine(MOCKED_UUID_CUSTOMER_ID);

    // expect
    assertDoesNotThrow(NotFoundException::new);
  }

  @Test
  void shouldFailGetNonExistentCreditLineRecord() {

    // given
    when(creditLineRequestsRepository.findById(any(UUID.class))).thenThrow(new NotFoundException());

    // act and expect
    assertThrows(
        NotFoundException.class,
        () -> creditLineService.getCustomerCreditLine(MOCKED_UUID_CUSTOMER_ID));
  }
}
