package org.roy.credit.line.controllers;

import static org.roy.credit.line.constants.ApiEndpoints.REQUEST_CREDIT_LINE_ENDPOINT;
import static org.roy.credit.line.constants.ApiParameterNames.CUSTOMER_ID_HEADER;
import static org.roy.credit.line.constants.ApiParameterNames.FOUNDING_TYPE_HEADER;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.roy.credit.line.docs.CreditLineApi;
import org.roy.credit.line.enums.FoundingType;
import org.roy.credit.line.models.requests.PostRequestCreditLineRequestBody;
import org.roy.credit.line.models.responses.ContractResponse;
import org.roy.credit.line.models.responses.PostRequestCreditLineResponseBody;
import org.roy.credit.line.services.CreditLineService;
import org.roy.credit.line.services.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@Validated
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CreditLineController implements CreditLineApi {

  private final CreditLineService creditLineService;
  private final RateLimitService rateLimitService;

  @Override
  @PostMapping(
      path = REQUEST_CREDIT_LINE_ENDPOINT,
      consumes = APPLICATION_JSON_VALUE,
      produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ContractResponse<PostRequestCreditLineResponseBody> requestCreditLine(
      @Valid @RequestBody PostRequestCreditLineRequestBody postRequestCreditLineRequestBody,
      @RequestHeader(value = CUSTOMER_ID_HEADER) UUID customerId,
      @RequestHeader(value = FOUNDING_TYPE_HEADER) FoundingType foundingType,
      HttpServletRequest servlet) {

    log.traceEntry(
        "Initializing credit line request validation. Request {}",
        postRequestCreditLineRequestBody.toString());

    rateLimitService.checkRateLimitFor(customerId);

    return log.traceExit(
        ContractResponse.<PostRequestCreditLineResponseBody>builder()
            .response(
                creditLineService.requestCreditLine(
                    customerId, postRequestCreditLineRequestBody, foundingType))
            .path(servlet.getServletPath())
            .build());
  }
}
