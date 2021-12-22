package org.mifos.connector.ams.pesacore.pesacore.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {
 *   "remoteTransactionId": "string",
 *   "phoneNumber": "+2547123456789",
 *   "account": "12345678",
 *   "amount": 123,
 *   "currency": "KES"
 * }
 */
public class PesacoreRequestDTO {

    @JsonProperty("remoteTransactionId")
    private String remoteTransactionId;

    @JsonProperty("phoneNumber")
    private String phoneNumber;

    @JsonProperty("account")
    private String account;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    public PesacoreRequestDTO() {
    }

    @Override
    public String toString() {
        return "PesacoreRequestDTO{" +
                "remoteTransactionId='" + remoteTransactionId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", account='" + account + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemoteTransactionId() {
        return remoteTransactionId;
    }

    public void setRemoteTransactionId(String remoteTransactionId) {
        this.remoteTransactionId = remoteTransactionId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
