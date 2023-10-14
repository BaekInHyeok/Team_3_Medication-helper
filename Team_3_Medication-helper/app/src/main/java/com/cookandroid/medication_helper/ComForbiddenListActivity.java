/****************************
 ComForbiddenListActivity.java
 작성 팀 : Hello World!
 주 작성자 : 백인혁
 프로그램명 : Medication Helper
 ***************************/
package com.cookandroid.medication_helper;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ComForbiddenListActivity extends AppCompatActivity {

    /* 의약품DB를 사용하기 위한 변수들 */
    UserData userData;
    String data;
    int listSize;

    ArrayList<String> medicList;

    @Override // 하단의 뒤로가기(◀) 버튼을 눌렀을 시 동작
    public void onBackPressed() {
        super.onBackPressed();
        Intent Back = new Intent(ComForbiddenListActivity.this, MedicineListActivity.class); // 메인화면으로 돌아가는 기능
        Back.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // 병용금지 페이지가 백그라운드에서 돌아가지 않도록 완전종료
        startActivity(Back); // 실행
        finish(); // Progress 완전 종료
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_comforbiddenlist);
        getSupportActionBar().setDisplayShowTitleEnabled(false); // 기본 타이틀 사용 안함
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM); // 커스텀 사용
        getSupportActionBar().setCustomView(R.layout.fortitlebar_custom); // 커스텀 사용할 파일 위치

        userData = (UserData) getApplicationContext();

        Button btnBack = findViewById(R.id.btnback_comforbid);
        ListView comXList = findViewById(R.id.combinationXList);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Medicine");
        medicList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, medicList);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.child(userData.getUserID()).getChildren()) {
                    String value = ds.getKey();
                    System.out.println("Data : " + value);
                    medicList.add(value);


                }

                listSize=medicList.size();
                System.out.println("약물 개수 : "+listSize);

                String[] mediclist = new String[listSize];

                for(int i=0;i<listSize;i++){
                    mediclist[i] = medicList.get(i);
                }

                for(int i=0;i<listSize;i++){
                    System.out.println("medicList 약물 : "+mediclist[i]);
                }

                String[][] medicNameINGList = new String[listSize][4];

                //OpenAPI XML 파싱 스레드
                new Thread(new Runnable() {

                    int forbiddenlistSize=0;
                    int index=0;

                    @Override
                    public void run() {

                        //약물목록에 있는 약 이름들을 이용하여 부작용 정보 내용(주성분,부작용)을 가져와 저장한다.
                        for(int i=0;i<listSize;i++){
                            data=getXmlData(mediclist[i]);

                            String []dataSplit=new String[3];

                            if(TextUtils.isEmpty(data)==false){
                                dataSplit= data.split("\n");
                            }

                            medicNameINGList[i][0]=mediclist[i];
                            System.out.println("약품명 : "+medicNameINGList[i][0]);
                            medicNameINGList[i][1]=dataSplit[0];
                            System.out.println("금기명 : "+medicNameINGList[i][1]);
                            medicNameINGList[i][2]=dataSplit[1];
                            System.out.println("유발성분명 : "+medicNameINGList[i][2]);
                            medicNameINGList[i][3]=dataSplit[2];
                            System.out.println("부작용 : "+medicNameINGList[i][3]);

                            DatabaseReference sideRef = FirebaseDatabase.getInstance().getReference("SideEffect");
                            Map<String, Object> comForbidUpdate = new HashMap<>();
                            if (medicNameINGList[i][2] != null)
                                comForbidUpdate.put("component", medicNameINGList[i][2]);
                            if (medicNameINGList[i][3] != null)
                                comForbidUpdate.put("cForbid", medicNameINGList[i][3]);
                            sideRef.child(medicNameINGList[i][0]).updateChildren(comForbidUpdate);
                        }

                        forbiddenlistSize=0;


                        for(int i=0;i<listSize;i++){
                            String str=medicNameINGList[i][1];
                            if(TextUtils.isEmpty(str)==false){
                                forbiddenlistSize++;
                            }
                        }

                        System.out.println("부작용 있는 약물 개수 : "+forbiddenlistSize);


                        //병용금기약물에 해당하는 약물들의 약물명만 따로 저장하는 리스트
                        String[] comXnameList = new String[forbiddenlistSize];

                        index=0;

                        for(int i=0;i<listSize;i++){
                            String str=medicNameINGList[i][1];
                            if(TextUtils.isEmpty(str)==false){
                                comXnameList[index]=medicNameINGList[i][0];//1열 : 약품명
                                index++;
                            }
                        }


                        //병용금기약물에 해당하는 약물들의 약물명, 금기명, 약물성분, 부작용 저장 2차원 배열
                        String[][] comXingList = new String[forbiddenlistSize][4];

                        index=0;

                        for(int i=0;i<listSize;i++){
                            String str=medicNameINGList[i][1];
                            if(TextUtils.isEmpty(str)==false){
                                comXingList[index][0]=medicNameINGList[i][0];//1열 : 약품명
                                comXingList[index][1]=medicNameINGList[i][1];//2열 : 금기명
                                comXingList[index][2]=medicNameINGList[i][2];//3열 : 약품 성분
                                comXingList[index][3]=medicNameINGList[i][3];//4열 : 약품 부작용
                                index++;
                            }
                        }

                        //병용금기사항 약물명 목록을 가지는 ArrayList
                        ArrayList<String> ComXMedication = new ArrayList<>(Arrays.asList(comXnameList));

                        ArrayAdapter ComXNameAdapter = new ArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_single_choice,ComXMedication);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //화면에 병용 금기 대상 약품 이름 목록 표시
                                comXList.setAdapter(ComXNameAdapter);

                                comXList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                    @Override
                                    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                                        String medicineName=(String) adapterView.getAdapter().getItem(position);
                                        String ingr="";
                                        String sideeffect="";

                                        for(int i=0;i<forbiddenlistSize;i++){
                                            if(medicineName.equals(comXingList[i][0])){
                                                ingr=comXingList[i][2];
                                                sideeffect=comXingList[i][3];
                                            }
                                        }
                                        showSideEffectDialog(medicineName, ingr, sideeffect);
                                    }
                                });

                            }
                        });
                    }

                    void showSideEffectDialog(String medicineName,String ingr, String sideeffect){
                        AlertDialog.Builder builder = new AlertDialog.Builder(ComForbiddenListActivity.this,R.style.AlertDialogTheme);
                        View view= LayoutInflater.from(ComForbiddenListActivity.this).inflate(R.layout.sideeffect_dialog1,(LinearLayout)findViewById(R.id.seDialog1));

                        builder.setView(view);
                        ((TextView)view.findViewById(R.id.medicname)).setText(medicineName);
                        ((TextView)view.findViewById(R.id.ingredient)).setText(ingr);
                        ((TextView)view.findViewById(R.id.sideffect)).setText(sideeffect);

                        AlertDialog alertDialog = builder.create();

                        view.findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                alertDialog.dismiss();
                            }
                        });

                        if(alertDialog.getWindow()!=null){
                            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable());
                        }

                        alertDialog.show();
                    }
                }).start();


            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getApplicationContext(),"알 수 없는 오류가 발생했습니다.",Toast.LENGTH_SHORT).show();
            }
        });


        btnBack.setOnClickListener(new View.OnClickListener() { // 뒤로가기 버튼을 눌렀을 경우
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ComForbiddenListActivity.this, MedicineListActivity.class); // 이전 화면으로 돌아가는 동작
                startActivity(intent); // 동작 시행
                finish(); // Progress 종료
            }
        });
    }



    //Xml 파싱으로 병용금기에 해당하는 약과 부작용 원인 성분 알아내기
    String getXmlData(String medicname) {
        StringBuffer buffer=new StringBuffer();
        String str=medicname;

        String queryUrl="http://apis.data.go.kr/1471000/DURPrdlstInfoService03/getUsjntTabooInfoList03?serviceKey=RZnyfUGsOhY2tWWUv262AHpeMQYn4Idqd5cgG0rGNHPd648m5j0Pu3eiS3ewN4XhhHT%2FvuliAmF9KLJdzh1TFA%3D%3D&pageNo=1&numOfRows=1&typeName=병용금기&itemName="+medicname;
        try {
            URL url=new URL(queryUrl);
            InputStream is=url.openStream();

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xpp=factory.newPullParser();
            xpp.setInput(new InputStreamReader(is,"UTF-8"));

            String tag;

            xpp.next();
            int eventType=xpp.getEventType();

            while(eventType != XmlPullParser.END_DOCUMENT){
                switch (eventType){

                    case XmlPullParser.START_TAG:
                        tag=xpp.getName();

                        if(tag.equals("item"));

                        else if(tag.equals("TYPE_NAME")){
                            xpp.next();
                            buffer.append(xpp.getText());
                            buffer.append("\n");
                        }

                        else if(tag.equals("INGR_KOR_NAME")){
                            xpp.next();
                            buffer.append(xpp.getText());
                            buffer.append("\n");
                        }
                        else if(tag.equals("PROHBT_CONTENT")){
                            xpp.next();
                            buffer.append(xpp.getText());
                        }
                        break;
                }
                eventType=xpp.next();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return buffer.toString();
    }
}
