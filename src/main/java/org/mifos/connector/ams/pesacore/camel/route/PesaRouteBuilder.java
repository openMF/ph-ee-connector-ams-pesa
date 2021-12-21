package org.mifos.connector.ams.pesacore.camel.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONObject;
import org.mifos.connector.ams.pesacore.pesacore.dto.PesacoreRequestDTO;
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


    @Override
    public void configure() {

        from("rest:POST:/api/paymentHub/Verification")
                .process(exchange -> {
                    JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
                    String transactionId = "test-transaction-id";
                    exchange.setProperty(CHANNEL_REQUEST, channelRequest);
                    exchange.setProperty(TRANSACTION_ID, transactionId);
                })
                .to("direct:transfer-validation-base");

        from("rest:POST:/api/paymentHub/Confirmation")
                .process(exchange -> {
                    JSONObject channelRequest = new JSONObject(exchange.getIn().getBody(String.class));
                    String transactionId = "test-transaction-id";
                    exchange.setProperty(CHANNEL_REQUEST, channelRequest);
                    exchange.setProperty(TRANSACTION_ID, transactionId);
                })
                .to("direct:transfer-settlement-base");

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
                })
                .otherwise()
                .log(LoggingLevel.ERROR, "Validation unsuccessful")
                .process(exchange -> {
                    // processing unsuccessful case
                    exchange.setProperty(PARTY_LOOKUP_FAILED, true);
                });

        from("direct:transfer-validation")
                .id("transfer-validation")
                .log(LoggingLevel.INFO, "## Starting transfer Validation route")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Content-Type", constant("application/json"))
                .setBody(exchange -> {
                    JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                    String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);

                    PesacoreRequestDTO verificationRequestDTO = getPesacoreDtoFromChannelRequest(channelRequest,
                            transactionId);

                    logger.info("Validation request DTO: \n\n\n" + verificationRequestDTO);
                    return verificationRequestDTO;
                })
                .marshal().json(JsonLibrary.Jackson)
                .toD(getVerificationEndpoint() + "?bridgeEndpoint=true&throwExceptionOnFailure=false")
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
                .setBody(exchange -> {

                    JSONObject channelRequest = (JSONObject) exchange.getProperty(CHANNEL_REQUEST);
                    String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);

                    PesacoreRequestDTO confirmationRequestDTO = getPesacoreDtoFromChannelRequest(channelRequest,
                            transactionId);
                    confirmationRequestDTO.setStatus("TEST-STATUS");

                    logger.info("Confirmation request DTO: \n\n\n" + confirmationRequestDTO);
                    return confirmationRequestDTO;
                })
                .marshal().json(JsonLibrary.Jackson)
                .toD(getConfirmationEndpoint() + "?bridgeEndpoint=true&throwExceptionOnFailure=false")
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

    private PesacoreRequestDTO getPesacoreDtoFromChannelRequest(JSONObject channelRequest, String transactionId) {
        PesacoreRequestDTO verificationRequestDTO = new PesacoreRequestDTO();

        String phoneNumber = channelRequest.getJSONObject("payer")
                .getJSONObject("partyIdInfo").getString("partyIdentifier");
        String accountId = channelRequest.getJSONObject("payee")
                .getJSONObject("partyIdInfo").getString("partyIdentifier");
        JSONObject amountJson = channelRequest.getJSONObject("amount");

        Long amount = amountJson.getLong("amount");
        String currency = amountJson.getString("currency");

        verificationRequestDTO.setRemoteTransactionId(transactionId);
        verificationRequestDTO.setAmount(amount);
        verificationRequestDTO.setPhoneNumber(phoneNumber);
        verificationRequestDTO.setCurrency(currency);
        verificationRequestDTO.setAccount(accountId);

        return verificationRequestDTO;
    }
}
