package org.mifos.connector.ams.pesacore.util;

import com.google.gson.Gson;
import org.apache.camel.util.json.JsonObject;

public class PesacoreUtils {

    public static String parseErrorDescriptionFromJsonPayload(String errorJson) {
        if (errorJson == null || errorJson.isEmpty()) {
            return null;
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
        return null;
    }

}
