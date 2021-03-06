package com.gephenom.smarthome.speech;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gephenom.smarthome.R;
import com.gephenom.smarthome.main.MyActivity;
import com.gephenom.smarthome.tools.Tools;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.RequestListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.VoiceWakeuper;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;
import com.iflytek.cloud.util.FileDownloadListener;
import com.iflytek.cloud.util.ResourceUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Created by freedom on 2016/10/4 0004.
 */

public class SpeechWake {


    private static String TAG = "freedom";
    private MyActivity myActivity;
    private Toast mToast;
    // 语音唤醒对象
    private VoiceWakeuper mIvw;
    // 语音识别对象
    private SpeechRecognizer mAsr;
    // 唤醒结果内容
    private String resultString;
    // 云端语法文件
    private String mCloudGrammar = null;
    // 识别结果内容
    private String recoString;
    // 云端语法id
    private String mCloudGrammarID;

    // 设置门限值 ： 门限值越低越容易被唤醒
    private final static int MAX = 60;
    private final static int MIN = -20;
    private int curThresh = -20;
    private String threshStr = "门限值：";
    private String keep_alive = "1";
    private String ivwNetMode = "0";

    public SpeechWake(MyActivity activity) {
        Log.d(TAG, "SpeechWake");
        myActivity=activity;
        mToast = Toast.makeText(myActivity, "", Toast.LENGTH_SHORT);
        // 初始化唤醒对象
        mIvw = VoiceWakeuper.createWakeuper(myActivity, null);
        // 初始化唤醒对象
        mIvw = VoiceWakeuper.createWakeuper(activity, null);
        // 初始化识别对象---唤醒+识别,用来构建语法
        mAsr = SpeechRecognizer.createRecognizer(activity, null);
        // 初始化语法文件
        mCloudGrammar = Tools.readFile(myActivity, "wake_grammar_sample.abnf", "utf-8");

        buildGrammar();
    }

    private void buildGrammar(){
        int ret = 0;
        // 设置参数
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        // 开始构建语法
        ret = mAsr.buildGrammar("abnf", mCloudGrammar, grammarListener);
        if (ret != ErrorCode.SUCCESS) {
            showTip("语法构建失败,错误码：" + ret);
        }
    }

    GrammarListener grammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if (error == null) {
                    mCloudGrammarID = grammarId;
                showTip("语法构建成功：" + grammarId);
                startWake();
            } else {
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };

    public void startWake(){
        mIvw = VoiceWakeuper.getWakeuper();
        if(mIvw != null) {
            resultString = "";
            recoString = "";

            // 清空参数
            mIvw.setParameter(SpeechConstant.PARAMS, null);
            // 唤醒门限值，根据资源携带的唤醒词个数按照“id:门限;id:门限”的格式传入
            mIvw.setParameter(SpeechConstant.IVW_THRESHOLD, "0:"+ curThresh+";1:"+ curThresh+";2:"+ curThresh+";3:"+ curThresh+";4:"+ curThresh);
            // 设置唤醒模式
            mIvw.setParameter(SpeechConstant.IVW_SST, "oneshot");
            // 设置返回结果格式
            mIvw.setParameter(SpeechConstant.RESULT_TYPE, "json");
            // 设置识别引擎
            mIvw.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            // 设置持续进行唤醒
            mIvw.setParameter(SpeechConstant.KEEP_ALIVE, keep_alive);
            // 设置闭环优化网络模式
            mIvw.setParameter(SpeechConstant.IVW_NET_MODE, ivwNetMode);
            // 设置唤醒资源路径
            mIvw.setParameter(SpeechConstant.IVW_RES_PATH, getResource());

            if (!TextUtils.isEmpty(mCloudGrammarID)) {
                // 设置云端识别使用的语法id
                mIvw.setParameter(SpeechConstant.CLOUD_GRAMMAR,
                        mCloudGrammarID);
                mIvw.startListening(mWakeuperListener);
            } else {
                showTip("请先构建语法");
            }

            // 启动唤醒
           // mIvw.startListening(mWakeuperListener);

            Log.d(TAG, "启动唤醒");
        } else {
            showTip("唤醒未初始化");
        }

        //mIvw.stopListening();
    }






    private WakeuperListener mWakeuperListener = new WakeuperListener() {

        @Override
        public void onResult(WakeuperResult result) {
            Log.d(TAG, "onResult");
            if(!"1".equalsIgnoreCase(keep_alive)) {
            }
            try {
                String text = result.getResultString();
                JSONObject object;
                object = new JSONObject(text);
                StringBuffer buffer = new StringBuffer();
                buffer.append("【RAW】 "+text);
                buffer.append("\n");
                buffer.append("【操作类型】"+ object.optString("sst"));
                buffer.append("\n");
                buffer.append("【唤醒词id】"+ object.optString("id"));
                buffer.append("\n");
                buffer.append("【得分】" + object.optString("score"));
                buffer.append("\n");
                buffer.append("【前端点】" + object.optString("bos"));
                buffer.append("\n");
                buffer.append("【尾端点】" + object.optString("eos"));
                resultString =buffer.toString();
            } catch (JSONException e) {
                resultString = "结果解析出错";
                e.printStackTrace();
            }
            Log.d(TAG, "resultString:"+resultString);
        }

        @Override
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true));
        }

        @Override
        public void onBeginOfSpeech() {
            //showTip("开始说话");
            Log.d(TAG, "开始说话:");
        }

        @Override
        public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
            Log.d(TAG, "eventType:"+eventType+ "arg1:"+isLast + "arg2:" + arg2);
            // 识别结果
            if (SpeechEvent.EVENT_IVW_RESULT == eventType) {
                RecognizerResult reslut = ((RecognizerResult)obj.get(SpeechEvent.KEY_EVENT_IVW_RESULT));
                recoString += parseGrammarResult(reslut.getResultString());
                if(!recoString.equals("没有匹配结果.")){myActivity.nextMusic();}
                showTip(recoString);
                startWake();
            }
        }

        @Override
        public void onVolumeChanged(int volume) {

        }
    };


    public static String parseGrammarResult(String json) {
        StringBuffer ret = new StringBuffer();
        try {
            JSONTokener tokener = new JSONTokener(json);
            JSONObject joResult = new JSONObject(tokener);

            JSONArray words = joResult.getJSONArray("ws");
            for (int i = 0; i < words.length(); i++) {
                JSONArray items = words.getJSONObject(i).getJSONArray("cw");
                for(int j = 0; j < items.length(); j++)
                {
                    JSONObject obj = items.getJSONObject(j);
                    if(obj.getString("w").contains("nomatch"))
                    {
                        ret.append("没有匹配结果.");
                        return ret.toString();
                    }
                    ret.append("【结果】" + obj.getString("w"));
                    ret.append("【置信度】" + obj.getInt("sc"));
                    ret.append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret.append("没有匹配结果.");
        }
        return ret.toString();
    }



    private String getResource() {
        return ResourceUtil.generateResourcePath(myActivity,
                ResourceUtil.RESOURCE_TYPE.assets, "ivw/"+myActivity.getString(R.string.app_id)+".jet");
    }

    private void showTip(final String str) {
        myActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    //    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        Log.d(TAG, "onDestroy WakeDemo");
//        // 销毁合成对象
//        mIvw = VoiceWakeuper.getWakeuper();
//        if (mIvw != null) {
//            mIvw.destroy();
//        }
//    }


}
