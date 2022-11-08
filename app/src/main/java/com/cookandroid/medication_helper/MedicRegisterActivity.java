package com.cookandroid.medication_helper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;

public class MedicRegisterActivity extends AppCompatActivity {

    Bitmap image;
    Bitmap bitmap;
    Bitmap rotatedbitmap;

    private TessBaseAPI mTess;
    String datapath = "";

    PreviewView previewView;
    Button btnStartCamera;
    Button btnCaptureCamera;
    TextView textView;

    ProcessCameraProvider processCameraProvider;
    int lensFacing = CameraSelector.LENS_FACING_BACK;
    ImageCapture imageCapture;

    String OCRresult;

    Button btnRegister;
    private String imageFilepath;
    static final int REQUEST_IMAGE_CAPTURE = 672;

    String[] EdiCodearray;//EDI 코드 목록을 저장하는 배열
    String[] medicList;//OpenAPI를 이용해 의약품 이름 목록을 저장하는 배열
    String data;

    UserData userData;
    com.cookandroid.medication_helper.MedicDBHelper myHelper;
    SQLiteDatabase sqlDB;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_medicregister);

        userData = (UserData) getApplicationContext();
        myHelper = new com.cookandroid.medication_helper.MedicDBHelper(this);
        previewView = (PreviewView) findViewById(R.id.previewView);
        btnStartCamera = (Button) findViewById(R.id.btnCameraStart);
        btnCaptureCamera = (Button) findViewById(R.id.btnPicture);
        textView = (TextView) findViewById(R.id.OCRTextResult);

        //언어 파일 경로 설정
        datapath = getFilesDir() + "/tessaract/";

        //언어 파일 존재 여부 확인
        checkFile(new File(datapath + "tessdata/"), "eng");

        String lang = "eng";

        mTess = new TessBaseAPI();//TessBaseAPI 생성
        mTess.init(datapath, lang);//초기화

        //숫자만 인식해서 추출하도록 블랙리스트, 화이트리스트 설정
        mTess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, ".,!?@#$%&*()<>_-+=/:;'\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
        mTess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789");

        //카메라 촬영을 위한 동의 얻기
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        try {
            processCameraProvider = processCameraProvider.getInstance(this).get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //카메라 프리뷰 작동
        btnStartCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(MedicRegisterActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    previewView.setVisibility(View.VISIBLE);
                    textView.setVisibility(View.INVISIBLE);
                    btnStartCamera.setVisibility(View.INVISIBLE);
                    btnStartCamera.setEnabled(false);
                    btnCaptureCamera.setVisibility(View.VISIBLE);
                    btnCaptureCamera.setEnabled(true);

                    bindPreview();
                    bindImageCapture();

                }
            }
        });

        //카메라에 접근해 사진 찍는 버튼
        btnCaptureCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageCapture.takePicture(ContextCompat.getMainExecutor(MedicRegisterActivity.this),
                        new ImageCapture.OnImageCapturedCallback() {
                            @Override
                            public void onCaptureSuccess(@NonNull ImageProxy image) {
                                @SuppressLint("UnsafeExperimentalUsageError")
                                Image mediaImage = image.getImage();
                                bitmap = com.cookandroid.medication_helper.ImageUtil.mediaImageToBitmap(mediaImage);

                                Log.d("MainActivity", Integer.toString(bitmap.getWidth())); //4128
                                Log.d("MainActivity", Integer.toString(bitmap.getHeight())); //3096

                                //imageView.setImageBitmap(bitmap);
                                rotatedbitmap = com.cookandroid.medication_helper.ImageUtil.rotateBitmap(bitmap, image.getImageInfo().getRotationDegrees());

                                Log.d("MainActivity", Integer.toString(rotatedbitmap.getWidth())); //3096
                                Log.d("MainActivity", Integer.toString(rotatedbitmap.getHeight())); //4128
                                Log.d("MainAtivity", Integer.toString(image.getImageInfo().getRotationDegrees()));
                                //90 //0, 90, 180, 90 //이미지를 바르게 하기위해 시계 방향으로 회전해야할 각도

                                processCameraProvider.unbindAll();//카메라 프리뷰 중단
                                //pictureImage.setImageBitmap(rotatedBitmap);
                                previewView.setVisibility(View.INVISIBLE);
                                //pictureImage.setVisibility(View.VISIBLE);

                                super.onCaptureSuccess(image);

                                int height = rotatedbitmap.getHeight();
                                int width = rotatedbitmap.getWidth();

                                //AlertDialog에 사용할 비트맵 이미지의 사이즈를 가로세로 비율 맞춰 축
                                Bitmap popupBitmap = Bitmap.createScaledBitmap(rotatedbitmap, 1000, height / (width / 1000), true);

                                //카메라 바인딩 사용중단
                                processCameraProvider.unbindAll();

                                ImageView capturedimage = new ImageView(MedicRegisterActivity.this);
                                capturedimage.setImageBitmap(popupBitmap);

                                //사진 촬영 결과를 AlertDialog로 띄워 사용 여부를 선택한다
                                AlertDialog.Builder captureComplete = new AlertDialog.Builder(MedicRegisterActivity.this)
                                        .setTitle("촬영 결과")
                                        .setMessage("이 사진을 사용할까요?")
                                        .setView(capturedimage)
                                        //사용을 선택할 경우 OCR 실행
                                        .setPositiveButton("사용", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
//                                                String OCRresult=null;
//                                                mTess.setImage(rotatedbitmap);
//                                                OCRresult=mTess.getUTF8Text();
//
//                                                textView.setText(OCRresult);
                                                startActivity(new Intent(getApplicationContext(), com.cookandroid.medication_helper.MainPageActivity.class));
                                            }
                                        })
                                        //재촬영을 선택할 경우 bitmap에 저장된 비트맵 파일을 지우고 다시 카메라 프리뷰를 바인딩함
                                        .setNegativeButton("재촬영", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                bitmap = null;
                                                bindPreview();
                                                bindImageCapture();
                                                textView.setVisibility(View.INVISIBLE);
                                                //pictureImage.setVisibility(View.INVISIBLE);
                                                previewView.setVisibility(View.VISIBLE);
                                            }
                                        });

                                captureComplete.setCancelable(false);

                                captureComplete.create().show();
                            }
                        });
            }
        });


//        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNav);
//        //Button btnPill = findViewById(R.id.pillbtn);
//        //Button btnJar = findViewById(R.id.jarbtn);
//        bottomNavigationView.setSelectedItemId(R.id.cameraNav);
//
//        //바텀네비게이션을 나타나게 해주는 함수
//        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
//            @Override
//            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
//                switch (item.getItemId()) {
//                    //home버튼을 누르면 액티비티 화면을 전환시켜준다
//                    case R.id.homeNav:
//                        startActivity(new Intent(getApplicationContext(), com.cookandroid.medication_helper.MainPageActivity.class));
//                        overridePendingTransition(0, 0);
//                        finish();
//                        return true;
//                    //현재 화면에서 보여주는 액티비티
//                    case R.id.cameraNav:
//                        return true;
//                    //article 버튼을 누르면 액티비티 화면을 전환시켜준다
//                    case R.id.articleNav:
//                        startActivity(new Intent(getApplicationContext(), MedicineListActivity.class));
//                        overridePendingTransition(0, 0);
//                        finish();
//                        return true;
//                    //user 버튼을 누르면 액티비티 화면을 전환시켜준다
//                    case R.id.userNav:
//                        startActivity(new Intent(getApplicationContext(), com.cookandroid.medication_helper.MyPageActivity.class));
//                        overridePendingTransition(0, 0);
//                        finish();
//                        return true;
//                }
//                return false;
//            }
//        });
    }

    void bindPreview(){
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        processCameraProvider.bindToLifecycle(this,cameraSelector,preview);
    }

    void bindImageCapture(){
        CameraSelector cameraSelector=new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();
        imageCapture=new ImageCapture.Builder()
                .build();

        processCameraProvider.bindToLifecycle(this,cameraSelector,imageCapture);
    }

    //스마트폰에 사진 파일 복사
    private void copyFiles(String lang){
        try{
            String filepath=datapath+"/tessdata/"+lang+".traineddata";

            AssetManager assetManager=getAssets();

            InputStream inputStream=assetManager.open("tessdata/"+lang+".traineddata");
            OutputStream outputStream=new FileOutputStream(filepath);

            byte[] buffer=new byte[1024];
            int read;

            while ((read=inputStream.read(buffer))!=-1){
                outputStream.write(buffer,0,read);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    //스마트폰에 파일이 있는 지 확인
    private void checkFile(File dir, String lang){
        if(!dir.exists()&&dir.mkdirs()){
            copyFiles(lang);
        }
        if(dir.exists()){
            String datafilepath=datapath+"/tessdata/"+lang+".traineddata";
            File datafile=new File(datafilepath);
            if(!datafile.exists()){
                copyFiles(lang);
            }
        }
    }


    /*
    Bitmap image;//사용되는 이미지
    private TessBaseAPI mTess;//Tess API Reference
    String datapath="";//언어데이터가 있는 경로

    Button btnCamera;//카메라버튼
    ImageView pictureImage;//사진 표시하는 이미지뷰

    Button btnOCR;//OCR버튼
    TextView OCRTextView;//OCR한 EDI 코드 목록을 표시하는 TextView

    Uri photoUri;
    String OCRresult;

    Button btnRegister;//등록버튼
    Button btnBacktoMain;//메인화면복귀버튼

    private String imageFilePath;
    static final int REQUEST_IMAGE_CAPTURE = 672;

    String[] EdiCodearray;//EDI 코드 목록을 저장하는 스트링 배열

    String[] medicList;//OpenAPI를 이용해 받아온 의약품 이름 목록을 저장하는 배열

    String data;
     */

    /* 의약품 DB를 사용하기 위한 변수들 */
    /*
    UserData userData;
    MedicDBHelper myHelper;
    SQLiteDatabase sqlDB;

     */

    /*스마트폰의 뒤로가기 버튼에 대한 뒤로가기 동작 구현*/
    /*
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent BackToMain = new Intent(MedicRegisterActivity.this, MainPageActivity.class);
        BackToMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(BackToMain);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_medicregister);
        setTitle("Medication Helper");

        userData = (UserData)getApplicationContext();
        myHelper = new MedicDBHelper(this);
        btnCamera=(Button) findViewById(R.id.btnPicture);
        btnOCR=(Button)findViewById(R.id.btnOCR);
        btnRegister=(Button)findViewById(R.id.regimedicbtn);
        OCRTextView=(TextView) findViewById(R.id.OCRTextResult);
        pictureImage=(ImageView)findViewById(R.id.CameraPicture);


        //언어 파일 경로 설정
        datapath=getFilesDir()+"/tessaract/";

        //언어 파일 존재 여부 확인
        checkFile(new File(datapath+"tessdata/"),"eng");

        String lang="eng";

        mTess=new TessBaseAPI();//TessBaseAPI 생성
        mTess.init(datapath,lang);//초기화

        //숫자만 인식해서 추출하도록 블랙리스트, 화이트리스트 설정
        mTess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, ".,!?@#$%&*()<>_-+=/:;'\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
        mTess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789");


        //카메라에 접근해 사진 찍는 버튼
        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendTakePhotoIntent();
                ((ImageView)findViewById(R.id.CameraPicture)).setVisibility(View.VISIBLE);
                OCRTextView.setVisibility(View.INVISIBLE);
            }
        });


        //OCR로 EDI_Code 추출하는 버튼
        btnOCR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BitmapDrawable d=(BitmapDrawable) ((ImageView)findViewById(R.id.CameraPicture)).getDrawable();
                image=d.getBitmap();

                OCRTextView.setVisibility(View.VISIBLE);
                pictureImage.setVisibility(View.INVISIBLE);

                OCRresult=null;
                mTess.setImage(image);

                OCRresult=mTess.getUTF8Text();

                OCRTextView.setText(OCRresult);

                //String array에 줄 단위로 저장 -> 이걸로 약 데이터 생성하면 됨
                EdiCodearray=OCRresult.split("\n");

                //api를 통해 받아온 약 목록을 저장
                medicList=new String[EdiCodearray.length];



                Toast.makeText(getApplicationContext(), "화면의 코드와 처방전의 코드가 일치하는지 확인해주세요", Toast.LENGTH_LONG).show();

            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(getApplicationContext(), "등록 중입니다. 잠시만 기다려주세요", Toast.LENGTH_LONG).show();

                sqlDB = myHelper.getWritableDatabase(); // 의약품 저장 DB를 쓰기 가능으로 불러옴
                Cursor cursor = sqlDB.rawQuery("SELECT * FROM medicTBL;", null);

                switch(view.getId()){
                    case R.id.regimedicbtn:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                for(int i=0;i<EdiCodearray.length;i++){
                                    data=getXmlData(EdiCodearray[i]);//줄에 EDI 코드로 약품명 받아오기
                                    medicList[i]=data;//약 품목 리스트에 저장
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {

     */
                                        /* 읽어들인 약품을 의약품 DB에 저장 */
    /*
                                        int count = cursor.getCount() + 1;
                                        for (int i = 0; i < medicList.length; i++) {
                                            sqlDB.execSQL("INSERT INTO medicTBL VALUES ("
                                                    + count + i + ", '"
                                                    + userData.getUserID() + "', '"
                                                    + medicList[i] + "');");
                                        }

                                        Intent BackToMain = new Intent(MedicRegisterActivity.this, MainPageActivity.class); // 메인화면으로 돌아가는 기능
                                        BackToMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // 백그라운드에서 실행되지 않도록 플래그 삭제
                                        startActivity(BackToMain); // 실행

                                        Toast.makeText(getApplicationContext(), "처방약이 등록되었습니다", Toast.LENGTH_LONG).show();

                                        cursor.close();
                                        sqlDB.close();
                                    }
                                });
                            }
                        }).start();
                        break;
                }
            }
        });
    }

    //Xml에서 데이터 가져오기
    String getXmlData(String edicode){
        StringBuffer buffer=new StringBuffer();
        String str=edicode;
        String medicName= URLEncoder.encode(str);


        String queryUrl="http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService02/getDrugPrdtPrmsnDtlInq01?serviceKey=RZnyfUGsOhY2tWWUv262AHpeMQYn4Idqd5cgG0rGNHPd648m5j0Pu3eiS3ewN4XhhHT%2FvuliAmF9KLJdzh1TFA%3D%3D&pageNo=1&numOfRows=3&type=xml&edi_code="+medicName;
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
                        else if(tag.equals("ITEM_NAME")){
                            xpp.next();
                            buffer.append(xpp.getText());
                            buffer.append("\n");
                        }
                        break;
                    case XmlPullParser.TEXT:
                        break;
                    case XmlPullParser.END_TAG:
                        tag=xpp.getName();

                        if(tag.equals("item"))buffer.append("\n");
                }
                eventType=xpp.next();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return buffer.toString();
    }

    //상수를 받아 각도를 변환
    private int exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    //비트맵을 각도대로 회전시켜 결과를 반환
    private Bitmap rotate(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    //카메라로 사진 찍어 이미지 띄우기
    private void sendTakePhotoIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }

            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this, getPackageName()+".fileprovider", photoFile);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    //intent로 비트맵 이미지 자체를 불러와서 이미지뷰에 출력
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_IMAGE_CAPTURE&&resultCode==RESULT_OK){
            ((ImageView)findViewById(R.id.CameraPicture)).setImageURI(photoUri);
            ExifInterface exif=null;

            Bitmap bitmap= BitmapFactory.decodeFile(imageFilePath);
            try{
                exif = new ExifInterface(imageFilePath);
            }catch(IOException e){
                e.printStackTrace();
            }

            int exifOrientation;
            int exifDegree;

            if(exif!=null){
                exifOrientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
                exifDegree=exifOrientationToDegrees(exifOrientation);
            }else{
                exifDegree=0;
            }
            pictureImage.setImageBitmap(rotate(bitmap,exifDegree));
        }
    }

    //이미지파일 생성
    private File createImageFile() throws IOException{
        String timeStamp=new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName="TEST_"+timeStamp+"_";
        File storageDir=getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File StorageDir=getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image=File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        imageFilePath=image.getAbsolutePath();
        return image;
    }

    //스마트폰에 사진 파일 복사
    private void copyFiles(String lang){
        try{
            String filepath=datapath+"/tessdata/"+lang+".traineddata";

            AssetManager assetManager=getAssets();

            InputStream inputStream=assetManager.open("tessdata/"+lang+".traineddata");
            OutputStream outputStream=new FileOutputStream(filepath);

            byte[] buffer=new byte[1024];
            int read;

            while ((read=inputStream.read(buffer))!=-1){
                outputStream.write(buffer,0,read);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    //스마트폰에 파일이 있는 지 확인
    private void checkFile(File dir, String lang){
        if(!dir.exists()&&dir.mkdirs()){
            copyFiles(lang);
        }
        if(dir.exists()){
            String datafilepath=datapath+"/tessdata/"+lang+".traineddata";
            File datafile=new File(datafilepath);
            if(!datafile.exists()){
                copyFiles(lang);
            }
        }
    }

     */
}
