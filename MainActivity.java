package com.gbsoft.ellosseum;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.gbsoft.ellosseum.databinding.ActivityMainBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivityLog";

    private static final int DelayMilliSeconds = 1000; // 1초
    private static final int CountDown = 3; // 카운트 다운 3초
    private ActivityMainBinding mBinding;
    private SharedPreferences mSharedPreferences;
    private String mUniqueId = "";
    private long mBackKeyPressedTime = 0;
    private boolean mIsService = false; // TODO : 서비스 시작/정지 버튼에 사용됨 (버튼 삭제시 변수 삭제)
    private int mCnt = CountDown; // 동작횟수를 카운트 해줄 변수
    private boolean mIsExecution = false;
    private String mType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        // getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); // 캡처 화면 방지 기능
        setContentView(mBinding.getRoot());

        overridePendingTransition(R.anim.fadein, R.anim.fadeout);
        this.initialSet();

        // TODO: GPS 시작 종료 기능 완성 시 버튼 및 기능 삭제
        mBinding.btnGpsStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsService) {
                    Common.stopLocationService(getApplicationContext(),
                            Common.isLocationServiceRunning(MainActivity.this));
                    mBinding.btnGpsStop.setText("Start");
                } else {
                    Common.startLocationService(getApplicationContext(),
                            Common.isLocationServiceRunning(MainActivity.this));
                    mBinding.btnGpsStop.setText("Stop");
                }
                mIsService = !mIsService;
            }
        });

        // TODO : 언어 설정 구현되면 삭제
        // mBinding.btnSetting.setVisibility(View.GONE);
    }

    // 초기 세팅
    @SuppressLint("CommitPrefEdits")
    public void initialSet() {
        mSharedPreferences = getSharedPreferences("Info", MODE_PRIVATE);
        // 유저아이디 저장
        Common.getUserId(mSharedPreferences);
        // mBinding.txtId.setText(Common.sUserId);

        mIsService = Common.isLocationServiceRunning(MainActivity.this);
        if (mIsService)
            mBinding.btnGpsStop.setText("stop");
        else
            mBinding.btnGpsStop.setText("start");

        this.checkLoginUser();
        this.getIntentData();
    }

    // 로그인 유저 확인
    private void checkLoginUser() {
        if (Common.sAuthority == Common.EMP) { // 로그인 유저가 근로자일 경우
            mBinding.btnEmployeeAuth.setVisibility(View.GONE); // 근로자 승인 버튼 숨기기
            mBinding.btnMap.setVisibility(View.GONE);
            // Common.startLocationService(getApplicationContext(),
            // Common.isLocationServiceRunning(MainActivity.this)); // GPS 서비스 실행
            // isService = true; // 서비스 실행중
        } else {
            // Common.startLocationService(getApplicationContext(),
            // Common.isLocationServiceRunning(MainActivity.this));
            // isService = true; // 서비스 실행중
            mBinding.btnRemedy.setVisibility(View.GONE); // 신문고 버튼 숨기기
            mBinding.btnAttendance.setVisibility(View.GONE); // 근태현황 버튼 숨기기
        }
    }

    // 넘겨받은 Intent 의 데이터
    private void getIntentData() {
        Intent intent = getIntent();
        if (intent.getStringExtra("type") == null) { // type 이 없을 때 - 토큰 갱신만 동작
            mType = "N";
            getNewToken();
        } else {
            mType = intent.getStringExtra("type"); // type 이 있을 때
            if (mType.equals("sos") || mType.equals("restrictedIn") || mType.equals("siteOut")) { // 구조요청이나 위험구역 진입 시
                getEmployeeLocation(intent.getStringExtra("empId")); // 근로자 위치 지도 표시
            } else {
                getNewToken(); // 나머지 토큰 갱신민 동작
            }
        }
        Log.v(TAG, "mType = " + mType);
    }

    /**
     * Get the token of the machine where the app is installed.
     * Store the token in SharedPreferences, because it is initialized each time a
     * new load is made.
     */
    // 토큰 갱신
    private void getNewToken() {
        Task<String> token = FirebaseMessaging.getInstance().getToken();
        token.addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (task.isSuccessful()) {
                    mUniqueId = task.getResult();
                    Log.v(TAG, mUniqueId);
                    uniqueIdUpdate();
                }
            }
        });
    }

    // FCM 토큰 업데이트
    public void uniqueIdUpdate() {
        @SuppressLint("SimpleDateFormat")
        String currentTime = Common.getCurrentTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        Call<ResponseBody> call = Common.sService_site.uniqueIdUpdate_(mUniqueId, currentTime, Common.sToken);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NotNull Call<ResponseBody> call, @NotNull Response<ResponseBody> response) {
                int code = response.code();
                Log.v(TAG, "uniqueIdUpdate code = " + code);
                if (code == 200) {
                    // 토큰 변동 없음
                    Log.d(TAG, "FCM 토큰 변동 없음");
                } else if (code == 201) {
                    // 토큰 등록
                    Log.d(TAG, "FCM 토큰 등록");
                } else if (code == 401) {
                    if (!MainActivity.this.isFinishing())
                        Common.appRestart(MainActivity.this);
                } else {
                    if (!MainActivity.this.isFinishing())
                        Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                                getString(R.string.dialog_response_error_content), () -> {
                                });
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (!MainActivity.this.isFinishing())
                    Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                            getString(R.string.dialog_connect_error_content), () -> {
                            });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 설정
        mBinding.btnSetting.setOnClickListener(settingClick);
        // SOS 요청
        mBinding.btnSos.setOnLongClickListener(sosLongClick);
        mBinding.btnSos.setOnClickListener(sosClick);
        mBinding.btnSos.setOnTouchListener(sosTouch);
        // 근로자 승인
        mBinding.btnEmployeeAuth.setOnClickListener(authClick);
        // 현장 신문고
        mBinding.btnRemedy.setOnClickListener(remedyClick);
        // 공지사항
        mBinding.btnNotice.setOnClickListener(noticeClick);
        // 근태현황
        mBinding.btnAttendance.setOnClickListener(attendanceClick);
        // 이슈사진
        mBinding.btnIssue.setOnClickListener(issueClick);
        // 현장지도
        mBinding.btnMap.setOnClickListener(mapClick);

        mBinding.btnMap.setEnabled(true);
    }

    // 설정 클릭
    View.OnClickListener settingClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), SettingActivity.class);
            intent.putExtra("type", "main");
            startActivity(intent);
        }
    };

    // SOS 구조 요청 버튼 길게 클릭 이벤트
    View.OnLongClickListener sosLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            // TODO : 관리자 SOS 구조요청 구현 (현장 DB 관리 기능 구현 )
            if (Common.sAuthority == Common.EMP) {
                Common.startLocationService(MainActivity.this, Common.isLocationServiceRunning(MainActivity.this));
                mCnt = CountDown;
                mHandler.sendEmptyMessageDelayed(0, DelayMilliSeconds);
                mBinding.btnSosText.setText(String.valueOf(mCnt));
            }
            return false;
        }
    };
    // sos 카운트다운 핸들러
    Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            mCnt--; // 카운트 다운
            if (mCnt < 1) {
                if (!mIsExecution) {
                    Common.showToast(getApplicationContext(), getString(R.string.sos_send));
                    // getAdminTokenList();
                    saveEmergencyAlarmLog();
                    mIsExecution = true;
                }
                mCnt = 0;
                mBinding.btnSosText.setText("SOS");
            } else {
                mBinding.btnSosText.setText(String.valueOf(mCnt)); // 화면에 카운트 나타내줌

            }
            mHandler.sendEmptyMessageDelayed(0, DelayMilliSeconds);
        }
    };
    // sos 버튼 터치아웃 시 작동 (카운트다운 정지)
    View.OnClickListener sosClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mBinding.btnSosText.setText("SOS");
            mCnt = 3;
            mIsExecution = false;
            mHandler.removeMessages(0);
        }
    };
    // sos 버튼 터치 리스너
    View.OnTouchListener sosTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                mBinding.btnSosText.setText("SOS");
                mCnt = 3;
                mIsExecution = false;
                mHandler.removeMessages(0);
            }
            return false;
        }
    };

    // 긴급 알람 데이터 저장
    private void saveEmergencyAlarmLog() {
        String date = Common.getCurrentTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()));
        String remark = "";
        Call<ResponseBody> call = Common.sService_site.emergencyAlarm(Common.sUserId, date, 0, remark, -1);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                int code = response.code();
                if (code == 200) {
                    sendMessageToAdmins();
                } else {
                    if (!MainActivity.this.isFinishing())
                        Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                                getString(R.string.dialog_response_error_content), () -> {
                                });
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (!MainActivity.this.isFinishing())
                    Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                            getString(R.string.dialog_connect_error_content), () -> {
                            });
            }
        });
    }

    // 관리자들에게 알림 전송
    private void sendMessageToAdmins() {
        Call<ResponseBody> call = Common.sService_site.sosMessage(Common.sUserId);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                int code = response.code();
                if (code == 200) {
                    Common.showToast(getApplicationContext(), getString(R.string.sos_send));
                } else {
                    if (!MainActivity.this.isFinishing())
                        Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                                getString(R.string.dialog_response_error_content), () -> {
                                });
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (!MainActivity.this.isFinishing())
                    Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                            getString(R.string.dialog_connect_error_content), () -> {
                            });
            }
        });
    }

    // 근로자승인 버튼 클릭
    View.OnClickListener authClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(MainActivity.this, ScanQRCodeActivity.class);
            startActivity(intent);
        }
    };
    // 현장 신문고 버튼 클릭
    View.OnClickListener remedyClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), RemedyActivity.class);
            startActivity(intent);
        }
    };
    // 공지사항 버튼 클릭
    View.OnClickListener noticeClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), NoticeActivity.class);
            startActivity(intent);
        }
    };
    // 근태현황 버튼 클릭
    View.OnClickListener attendanceClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (Common.sAuthority == Common.EMP) {
                Intent intent = new Intent(getApplicationContext(), AttendanceActivity.class);
                startActivity(intent);
            }
        }
    };
    // 이슈사진 버튼 클릭
    View.OnClickListener issueClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), IssueManagementActivity.class);
            startActivity(intent);
        }
    };
    // 현장지도 버튼 클릭
    View.OnClickListener mapClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (Common.sAuthority == Common.EMP) {
                getEmployeeLocation(Common.sUserId);
            } else {
                Common.startLocationService(getApplicationContext(),
                        Common.isLocationServiceRunning(MainActivity.this)); // GPS 서비스 실행
                Common.sGpsHandler = mGpsHandler;
                mBinding.btnMap.setEnabled(false);
            }
        }
    };

    private final Handler mGpsHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == 1) {
                Common.sGpsHandler = null;
                Intent intent = new Intent(MainActivity.this, MapActivity.class);
                startActivity(intent);
            }
        }
    };

    // 근로자 마지막 위치 조회
    private void getEmployeeLocation(String id) {
        Call<ResponseBody> call = Common.sService_site.getEmployeeLocation(id);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NotNull Call<ResponseBody> call, @NotNull Response<ResponseBody> response) {
                int code = response.code();
                if (code == 200) {
                    try {
                        String result = response.body().string();
                        Log.v(TAG, "getEmployeeLocation result = " + result);
                        JSONObject jsonObject = new JSONObject(result);
                        Intent intent = new Intent(getApplicationContext(), MapActivity.class);
                        intent.putExtra("lat", jsonObject.getString("lat"));
                        intent.putExtra("lon", jsonObject.getString("lon"));
                        intent.putExtra("getUserGps", 1);
                        startActivity(intent);
                    } catch (JSONException e) {
                        Log.e(Common.TAG_ERR, "ERROR: JSON Parsing Error - getEmployeeLocation");
                        if (!MainActivity.this.isFinishing())
                            Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                                    getString(R.string.dialog_catch_error_content), () -> {
                                    });
                    } catch (IOException e) {
                        Log.e(Common.TAG_ERR, "ERROR: IOException - getEmployeeLocation");
                        if (!MainActivity.this.isFinishing())
                            Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                                    getString(R.string.dialog_catch_error_content), () -> {
                                    });
                    }
                } else if (code == 401) {
                    if (!MainActivity.this.isFinishing())
                        Common.appRestart(MainActivity.this);
                } else {
                    if (!MainActivity.this.isFinishing())
                        Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                                getString(R.string.dialog_response_error_content), () -> {
                                });
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (!MainActivity.this.isFinishing())
                    Common.showDialog(MainActivity.this, getString(R.string.dialog_error_title),
                            getString(R.string.dialog_connect_error_content), () -> {
                            });
            }
        });
    }

    // 뒤로가기 버튼 클릭
    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - mBackKeyPressedTime < 1500) {
            finishAffinity();
            System.runFinalization();
            System.exit(0);
        }
        mBackKeyPressedTime = System.currentTimeMillis();
        Common.showToast(getApplicationContext(), getString(R.string.exit_app));
    }
}