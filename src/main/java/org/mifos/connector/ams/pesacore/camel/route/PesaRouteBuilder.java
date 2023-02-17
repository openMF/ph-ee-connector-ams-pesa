package org.mifos.connector.ams.pesacore.camel.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONObject;
import org.mifos.connector.ams.pesacore.pesacore.dto.PesacoreRequestDTO;
import org.mifos.connector.ams.pesacore.util.ConnectionUtils;
import org.mifos.connector.ams.pesacore.util.PesacoreUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static org.mifos.connector.ams.pesacore.camel.config.CamelProperties.CHANNEL_REQUEST;
import static org.mifos.connector.ams.pesacore.zeebe.ZeebeVariables.*;

@Component
public class PesaRouteBuilder extends RouteBuilder {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${pesacore.base-url}")
    private String pesacoreBaseUrl;

    @Value("${pesacore.endpoint.verification}")
    private String verificationEndpoint;

    @Value("${pesacore.endpoint.confirmation}")
    private String confirmationEndpoint;

    @Value("${pesacore.auth-header}")
    private String authHeader;

    @Value("${ams.timeout}")
    private Integer amsTimeout;

    @Override
    public void configure() {

        from("rest:POST:/api/paymentHub/Verification")
                .process(exchange -> {
                    JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
                    String transactionId = "123";
                    exchange.setProperty(CHANNEL_REQUEST, channelRequest);
                    exchange.setProperty(TRANSACTION_ID, transactionId);
                })
                .to("direct:transfer-validation-base");

        from("rest:POST:/api/paymentHub/Confirmation")
                .process(exchange -> {
                    JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
                    String transactionId = "123";
                    exchange.setProperty(CHANNEL_REQUEST, channelRequest);
                    exchange.setProperty(TRANSACTION_ID, transactionId);
                })
                .to("direct:transfer-settlement-base");

        from("rest:POST:/api/v1/paybill/validate/roster")
                .id("validate-user")
                .log(LoggingLevel.INFO, "## Roster user validation")
                .setBody(e -> {
                    String body=e.getIn().getBody(String.class);
                    logger.debug("Body : {}",body);
                    e.setProperty("dfspId",e.getProperty("dfspId"));
                    return body;
                })
                .to("direct:transfer-validation-base")
                .process(e->{
                    String transactionId= e.getProperty(TRANSACTION_ID).toString();
                    logger.debug("Transaction Id : "+transactionId);
                    logger.debug("Response received from validation base : {}",e.getIn().getBody());
                    // Building the response
                    JSONObject responseObject=new JSONObject();
                    responseObject.put("reconciled", e.getProperty(PARTY_LOOKUP_FAILED).equals(false));
                    responseObject.put("AMS", "roster");
                    responseObject.put("transaction_id", transactionId);
                    logger.debug("response object "+responseObject);
                    e.getIn().setBody(responseObject.toString());
                });

        from("direct:transfer-validation-base")
                .id("transfer-validation-base")
                .log(LoggingLevel.INFO, "## Starting transfer Validation base route")
                .to("direct:transfer-validation")
                .choice()
                .when(header("CamelHttpResponseCode").isEqualTo("200"))
                .log(LoggingLevel.INFO, "Validation successful")
                .process(exchange -> {
                    // processing success case
                    exchange.setProperty(PARTY_LOOKUP_FAILED, false);
                    exchange.setProperty("dfspId",exchange.getProperty("dfspId"));
                    logger.debug("Pesacore Validation Success");
                })
                .otherwise()
                .log(LoggingLevel.ERROR, "Validation unsuccessful")
                .process(exchange -> {
                    // processing unsuccessful case
                    exchange.setProperty(PARTY_LOOKUP_FAILED, true);
                    exchange.setProperty("dfspId",exchange.getProperty("dfspId"));
                    logger.debug("Pesacore Validation Failure");
                });

        from("direct:transfer-validation")
                .id("transfer-validation")
                .log(LoggingLevel.INFO, "## Starting transfer Validation route")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Authorization", simple("Token " + authHeader))
                .setBody(exchange -> {
                    if(exchange.getProperty(CHANNEL_REQUEST)!=null) {
                        JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                        String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);

                        PesacoreRequestDTO verificationRequestDTO = buildPesacoreDtoFromChannelRequest(channelRequest,
                                transactionId);
                        logger.debug("Validation request DTO: \n\n\n" + verificationRequestDTO);
                        return verificationRequestDTO;
                    }
                    else {
                        JSONObject paybillRequest = new JSONObject(exchange.getIn().getBody(String.class));
                        PesacoreRequestDTO pesacoreRequestDTO = PesacoreUtils.convertPaybillPayloadToAmsPesacorePayload(paybillRequest);

                        String transactionId = pesacoreRequestDTO.getRemoteTransactionId();
                        log.info(pesacoreRequestDTO.toString());
                        exchange.setProperty(TRANSACTION_ID, transactionId);
                        exchange.setProperty("dfspId",exchange.getProperty("dfspId"));
                        logger.debug("Validation request DTO: \n\n\n" + pesacoreRequestDTO);
                        return pesacoreRequestDTO;
                    }
                })
                .marshal().json(JsonLibrary.Jackson)
                .toD(getVerificationEndpoint()+ "?bridgeEndpoint=true&throwExceptionOnFailure=false&"+
                        ConnectionUtils.getConnectionTimeoutDsl(amsTimeout))
                .log(LoggingLevel.INFO, "Pesacore verification api response: \n\n..\n\n..\n\n.. ${body}");

        from("direct:transfer-settlement-base")
                .id("transfer-settlement-base")
                .log(LoggingLevel.INFO, "## Transfer Settlement route")
                .to("direct:transfer-settlement")
                .choice()
                .when(header("CamelHttpResponseCode").isEqualTo("200"))
                .log(LoggingLevel.INFO, "Settlement successful")
                .process(exchange -> {
                    // processing success case

                    JSONObject body = new JSONObject(exchange.getIn().getBody(String.class));

                    if(body.getString("status").equals("CONFIRMED")) {
                        exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, false);
                    } else {
                        exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, true);
                    }
                })
                .otherwise()
                .log(LoggingLevel.ERROR, "Settlement unsuccessful")
                .process(exchange -> {
                    // processing unsuccessful case
                    exchange.setProperty(TRANSFER_SETTLEMENT_FAILED, true);
                });


        from("direct:transfer-settlement")
                .id("transfer-settlement")
                .log(LoggingLevel.INFO, "## Starting transfer settlement route")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Authorization", simple("Token " + authHeader))
                .setBody(exchange -> {
                    if(exchange.getProperty(CHANNEL_REQUEST).toString().contains("customData")){
                        JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                        String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);
                        String mpesaReceiptNumber = exchange.getProperty(EXTERNAL_ID, String.class);

                        PesacoreRequestDTO confirmationRequestDTO = buildPesacoreDtoFromChannelRequest(channelRequest,
                                mpesaReceiptNumber);
                        confirmationRequestDTO.setStatus("successful");
                        confirmationRequestDTO.setReceiptId(mpesaReceiptNumber);

                        logger.info("Confirmation request DTO: \n\n\n" + confirmationRequestDTO);
                        return confirmationRequestDTO;
                    }else {
                        JSONObject paybillRequest = new JSONObject(exchange.getIn().getBody(String.class));
                        PesacoreRequestDTO pesacoreRequestDTO = PesacoreUtils.convertPaybillPayloadToAmsPesacorePayload(paybillRequest);

                        String transactionId = pesacoreRequestDTO.getRemoteTransactionId();
                        log.debug(pesacoreRequestDTO.toString());
                        exchange.setProperty(TRANSACTION_ID, transactionId);
                        logger.debug("Confirmation request DTO: {}" ,pesacoreRequestDTO);
                        return pesacoreRequestDTO;
                    }
                })
                .marshal().json(JsonLibrary.Jackson)
                .toD(getConfirmationEndpoint() + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"+
                        ConnectionUtils.getConnectionTimeoutDsl(amsTimeout))
                .log(LoggingLevel.INFO, "Pesacore verification api response: \n\n..\n\n..\n\n.. ${body}");

    }


    // returns the complete URL for verification request
    private String getVerificationEndpoint() {
        return pesacoreBaseUrl + verificationEndpoint;
    }

    // returns the complete URL for confirmation request
    private String getConfirmationEndpoint() {
        return pesacoreBaseUrl + confirmationEndpoint;
    }

    private PesacoreRequestDTO buildPesacoreDtoFromChannelRequest(JSONObject channelRequest, String transactionId) {
        PesacoreRequestDTO pesacoreRequestDTO = new PesacoreRequestDTO();

        String phoneNumber = channelRequest.getJSONObject("payer")
                .getJSONObject("partyIdInfo").getString("partyIdentifier");
        String accountId = channelRequest.getJSONObject("payee")
                .getJSONObject("partyIdInfo").getString("partyIdentifier");
        JSONObject amountJson = channelRequest.getJSONObject("amount");

        Long amount = amountJson.getLong("amount");
        String currency = amountJson.getString("currency");

        pesacoreRequestDTO.setRemoteTransactionId(transactionId);
        pesacoreRequestDTO.setAmount(amount);
        pesacoreRequestDTO.setPhoneNumber(phoneNumber);
        pesacoreRequestDTO.setCurrency(currency);
        pesacoreRequestDTO.setAccount(accountId);

        return pesacoreRequestDTO;
    }
}