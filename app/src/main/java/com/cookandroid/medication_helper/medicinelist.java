package com.cookandroid.medication_helper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class medicinelist extends AppCompatActivity {

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent Back = new Intent(medicinelist.this, medicCheck.class);
        Back.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(Back);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.medicinelist);
        setTitle("Medication Helper");

        Button btnBack = findViewById(R.id.btnback1);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(medicinelist.this, medicCheck.class);
                startActivity(intent);
                finish();
            }
        });

    }
}
