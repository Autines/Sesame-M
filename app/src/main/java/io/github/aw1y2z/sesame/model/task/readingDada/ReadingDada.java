package io.github.aw1y2z.sesame.model.task.readingDada;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.model.normal.answerAI.AnswerAI;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;

/**
 * @author Constanline
 * @since 2023/08/22
 */
public class ReadingDada {
    private static final String TAG = ReadingDada.class.getSimpleName();

    public ModelGroup getGroup() {
        return ModelGroup.STALL;
    }

    public static boolean answerQuestion(JSONObject bizInfo) {
        try {
            String taskJumpUrl = bizInfo.optString("taskJumpUrl");
            if (StringUtil.isEmpty(taskJumpUrl)) {
                taskJumpUrl = bizInfo.getString("targetUrl");
            }
            // 原先用 split(...)[1] 直接取下标：链接里没有该参数、或参数正好在末尾（split 会丢掉末尾空串）
            // 都会抛 ArrayIndexOutOfBoundsException；getSubString 取不到时返回 ""
            String activityId = StringUtil.getSubString(taskJumpUrl, "activityId%3D", "%26");
            if (StringUtil.isEmpty(activityId)) {
                Log.record("答题跳过：跳转链接里没有 activityId");
                return false;
            }
            String outBizId = StringUtil.getSubString(taskJumpUrl, "outBizId%3D", "%26");
            String s = ReadingDadaRpcCall.getQuestion(activityId);
            JSONObject jo = new JSONObject(s);
            if ("200".equals(jo.getString("resultCode"))) {
                JSONArray jsonArray = jo.getJSONArray("options");
                String answer = AnswerAI.getAnswer(jo.getString("title"), JsonUtil.jsonArrayToList(jsonArray));
                if (answer == null || answer.isEmpty()) {
                    answer = jsonArray.getString(0);
                }
                s = ReadingDadaRpcCall.submitAnswer(activityId, outBizId, jo.getString("questionId"), answer);
                jo = new JSONObject(s);
                if ("200".equals(jo.getString("resultCode"))) {
                    Log.record("答题完成");
                    return true;
                } else {
                    Log.record("答题失败");
                }
            } else {
                Log.record("获取问题失败");
            }
        } catch (Throwable e) {
            Log.i(TAG, "answerQuestion err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
}