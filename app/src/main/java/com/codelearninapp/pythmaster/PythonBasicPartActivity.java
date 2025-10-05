package com.codelearninapp.pythmaster;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PythonBasicPartActivity extends AppCompatActivity {

    private int partNumber; // 1 to 5

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_python_part1); // your XML file

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );


        partNumber = getIntent().getIntExtra("part_number", 1);

        Button btnCheckKnowledge = findViewById(R.id.btnCheckKnowledge);
        btnCheckKnowledge.setOnClickListener(v -> {
            // Start the quiz, passing the part number
            Intent intent = new Intent(PythonBasicPartActivity.this, PythonBasicQuizActivity.class);
            intent.putExtra("part_number", partNumber);
            intent.putExtra("quiz_type", "python_basic");
            startActivity(intent);
        });
    }
}