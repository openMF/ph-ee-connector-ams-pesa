package org.mifos.connector.ams.pesacore.util;

import com.google.gson.Gson;
import org.apache.camel.util.json.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mifos.connector.ams.pesacore.pesacore.dto.PesacoreRequestDTO;

import static org.apache.camel.model.dataformat.JsonLibrary.Gson;

public class PesacoreUtils {

    public static String parseErrorDescriptionFromJsonPayload(String errorJson) {
        if (errorJson == null || errorJson.isEmpty()) {
            return "Internal Server Error";
        }
        try {
        JsonObject jsonObject = (new Gson()).fromJson(errorJson, JsonObject.class);
        String[] keyList = {"Message", "error", "errorDescription", "errorMessage", "description"};
        for (String s : keyList) {
            String data = jsonObject.getString(s);
            if (data != null && !data.isEmpty()) {
                return data;
            }
        }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return "Internal Server Error";
    }

    public static PesacoreRequestDTO convertPaybillPayloadToAmsPesacorePayload(JSONObject payload) {
        String transactionId = convertCustomData(payload.getJSONArray("customData"), "transactionId");
        String currency = convertCustomData(payload.getJSONArray("customData"), "currency");
        String wallet_msisdn=payload.getJSONObject("secondaryIdentifier").getString("value");
        String accountID=payload.getJSONObject("primaryIdentifier").getString("value");
        PesacoreRequestDTO validationRequestDTO = new PesacoreRequestDTO();
        validationRequestDTO.setAccount(accountID);
        validationRequestDTO.setAmount(1L);
        validationRequestDTO.setCurrency(currency);
        validationRequestDTO.setRemoteTransactionId(transactionId);
        validationRequestDTO.setPhoneNumber(wallet_msisdn);
        validationRequestDTO.setStatus(null);
        return validationRequestDTO;
    }
    public static String convertCustomData(JSONArray customData, String key)
    {
        for(Object obj: customData)
        {
            JSONObject item = (JSONObject) obj;
            try {
                String filter = item.getString("key");
                if (filter != null && filter.equalsIgnoreCase(key)) {
                    return item.getString("value");
                }
            } catch (Exception e){
            }
        }
        return null;
    }
}
