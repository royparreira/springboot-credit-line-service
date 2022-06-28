package org.roy.trb.tst.credit.line.exceptions;

import static org.roy.trb.tst.credit.line.enums.CreditLineStatus.REJECTED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import javax.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.roy.trb.tst.credit.line.constants.Descriptions;
import org.roy.trb.tst.credit.line.enums.ErrorType;
import org.roy.trb.tst.credit.line.models.responses.ContractResponse;
import org.roy.trb.tst.credit.line.models.responses.CreditLineApiResponse;
import org.roy.trb.tst.credit.line.models.responses.ResponseError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@Log4j2
@ControllerAdvice
public class CommonExceptionHandler {

  @ResponseBody
  @ExceptionHandler({MissingRequestHeaderException.class})
  public ResponseEntity<ContractResponse<Void>> handleMissingHeadersExceptions(
      HttpServletRequest request, MissingRequestHeaderException exception) {

    log.warn("Request header validation error occurred: {}", exception.getMessage());

    var contractResponse =
        ContractResponse.<Void>builder()
            .error(
                ResponseError.builder()
                    .errorCode(HttpStatus.BAD_REQUEST)
                    .errorType(ErrorType.MISSING_REQUIRED_HEADER)
                    .errorMessage(exception.getMessage())
                    .build())
            .path(request.getServletPath())
            .build();

    return new ResponseEntity<>(
        contractResponse, getProducesJsonHttpHeader(), HttpStatus.BAD_REQUEST);
  }

  @ResponseBody
  @ExceptionHandler({RejectedCreditLineException.class})
  public ResponseEntity<ContractResponse<CreditLineApiResponse>> handleRejectedCreditLineExceptions(
      HttpServletRequest request, RejectedCreditLineException exception) {

    log.info("Credit line request rejected!");

    String customMessage =
        exception.getCustomMessage().isEmpty() ? null : exception.getCustomMessage();

    var contractResponse =
        ContractResponse.<CreditLineApiResponse>builder()
            .response(
                CreditLineApiResponse.builder()
                    .creditLineStatus(REJECTED)
                    .message(customMessage)
                    .build())
            .path(request.getServletPath())
            .build();

    return new ResponseEntity<>(contractResponse, getProducesJsonHttpHeader(), HttpStatus.OK);
  }

  @ResponseBody
  @ExceptionHandler({NotFoundException.class})
  public ResponseEntity<ContractResponse<Void>> handleNotFoundExceptions(
      HttpServletRequest request, NotFoundException exception) {

    log.warn("Not found: {}", exception.getMessage());

    var contractResponse =
        ContractResponse.<Void>builder()
            .error(
                ResponseError.builder()
                    .errorCode(HttpStatus.NOT_FOUND)
                    .errorType(ErrorType.DATA_NOT_FOUND)
                    .errorMessage(Descriptions.NOT_FOUND_DESCRIPTION)
                    .build())
            .path(request.getServletPath())
            .build();

    return new ResponseEntity<>(
        contractResponse, getProducesJsonHttpHeader(), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  @ResponseBody
  public ResponseEntity<ContractResponse<Void>> handleException(
      HttpServletRequest request, Exception exception) {

    log.error("Unhandled exception: {} ", ExceptionUtils.getStackTrace(exception), exception);

    var contractResponse =
        ContractResponse.<Void>builder()
            .error(
                ResponseError.builder()
                    .errorCode(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorType(ErrorType.UNKNOWN_ERROR)
                    .errorMessage(Descriptions.INTERNAL_SERVER_ERROR_DESCRIPTION)
                    .build())
            .path(request.getServletPath())
            .build();

    return new ResponseEntity<>(
        contractResponse, getProducesJsonHttpHeader(), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private HttpHeaders getProducesJsonHttpHeader() {

    var defaultHttpHeaders = new HttpHeaders();
    defaultHttpHeaders.set("produces", APPLICATION_JSON_VALUE);

    return defaultHttpHeaders;
  }
}