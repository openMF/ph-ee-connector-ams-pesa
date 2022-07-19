package org.mifos.connector.ams.pesacore.zeebe;

public class ZeebeVariables {

    private ZeebeVariables() {
    }

    public static final String TRANSACTION_ID = "transactionId";
    public static final String PARTY_LOOKUP_FAILED = "partyLookupFailed";
    public static final String TRANSFER_SETTLEMENT_FAILED = "transferSettlementFailed";
    public static final String SERVER_TRANSACTION_ID = "mpesaTransactionId";
    public static final String ERROR_INFORMATION = "errorInformation";
    public static final String ERROR_CODE = "errorCode";
    public static final String ERROR_DESCRIPTION = "errorDescription";
    public static final String EXTERNAL_ID = "externalId";
}
