package io.github.aw1y2z.sesame.model.normal.answerAI;

import okhttp3.*;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.util.Log;

import java.util.List;

import static io.github.aw1y2z.sesame.util.JsonUtil.getValueByPath;

/**
 * GenAI帮助类
 *
 * @author Xiong
 */
public class GeminiAI implements AnswerAIInterface {
    private final String TAG = GeminiAI.class.getSimpleName();

    private final String url = "https://api.genai.gd.edu.kg/google";

    /** 复用同一个 OkHttpClient（自带连接池与线程），避免每次请求都新建 */
    private static final OkHttpClient CLIENT = new OkHttpClient();

    private final String token;

    // 私有构造函数，防止外部实例化
    public GeminiAI(String token) {
        if (token != null && !token.isEmpty()) {
            this.token = token;
        } else {
            this.token = "";
        }
        /*if (cUrl != null && !cUrl.isEmpty()) {
            url = cUrl.trim().replaceAll("/$", "");
        }*/
    }

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果
     */
    @Override
    public String getAnswerStr(String text) {
        String result = "";
        try {
            String content = "{\n" +
                    "    \"contents\": [\n" +
                    "        {\n" +
                    "            \"parts\": [\n" +
                    "                {\n" +
                    "                    \"text\": \"只回答答案 " + text + "\"\n" +
                    "                }\n" +
                    "            ]\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}";
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(content, mediaType);
            String url2 = url + "/v1beta/models/gemini-1.5-flash:generateContent?key=" + token;
            Request request = new Request.Builder()
                    .url(url2)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            // try-with-resources：成功、提前 return、异常三条路径都会关闭 Response，连接归还连接池
            try (Response response = CLIENT.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    return result;
                }
                String json = responseBody.string();
                if (!response.isSuccessful()) {
                    Log.other("Gemini请求失败");
                    Log.i("Gemini接口异常：" + json);
                    //可能key出错了
                    return result;
                }
                JSONObject jsonObject = new JSONObject(json);
                result = getValueByPath(jsonObject, "candidates.[0].content.parts.[0].text");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return result;
    }

    /**
     * 获取答案
     *
     * @param title     问题
     * @param answerList 答案集合
     * @return 空没有获取到
     */
    @Override
    public Integer getAnswer(String title, List<String> answerList) {
        StringBuilder answerStr = new StringBuilder();
        for (String answer : answerList) {
            answerStr.append("[").append(answer).append("]");
        }
        String answerResult = getAnswerStr(title + "\n" + answerStr);
        if (answerResult != null && !answerResult.isEmpty()) {
            for (int i = 0, size = answerList.size(); i < size; i++) {
                if (answerResult.contains(answerList.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }
}
