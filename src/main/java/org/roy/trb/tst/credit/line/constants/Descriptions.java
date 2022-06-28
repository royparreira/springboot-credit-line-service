package org.roy.trb.tst.credit.line.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Descriptions {

  /** Description for POST v1/validate 202 response */
  public static final String CREDIT_LINE_REQUEST_ACCEPTED_DESCRIPTION =
      "CREDIT LINE request was ACCEPTED";

  /** Description for POST v1/validate 202 response */
  public static final String CREDIT_LINE_REQUEST_REJECTED_DESCRIPTION =
      "Processing went well, but the CREDIT LINE request was REJECTED";

  /** Description for general internal server error */
  public static final String INTERNAL_SERVER_ERROR_DESCRIPTION =
      "Internal server error, please contact the system admin";

  /** Description for general internal server error */
  public static final String BAD_REQUEST_DESCRIPTION =
      "Bad request response, verify the request data";

  /** Description for general not found error */
  public static final String NOT_FOUND_DESCRIPTION = "Not Found response, verify the request data";

  /** Message if there is more than three rejected credit lines requests for the same customerId */
  public static final String SALES_AGENT_MSG = "A sales agent will contact you";
}
