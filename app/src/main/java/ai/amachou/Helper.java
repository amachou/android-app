package ai.amachou;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public class Helper {

    public static JSONObject loadJSONFile(Context ctx, String path) {
        try {
            InputStream stream = ctx.getAssets().open(path);
            byte[] buffer = new byte[stream.available()];
            stream.read(buffer);
            stream.close();
            return new JSONObject(new String(buffer, "UTF-8"));
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
