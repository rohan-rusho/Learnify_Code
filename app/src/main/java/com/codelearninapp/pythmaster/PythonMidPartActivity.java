package com.codelearninapp.pythmaster;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PythonMidPartActivity extends AppCompatActivity {

    private int partNumber; // 1..5

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use your mid part detail XML; ensure it has btnCheckKnowledge
        setContentView(R.layout.activity_mid_level_python_part1);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        partNumber = getIntent().getIntExtra("part_number", 1);

        Button btnCheckKnowledge = findViewById(R.id.btnCheckKnowledge);
        btnCheckKnowledge.setOnClickListener(v -> {
            Intent intent = new Intent(PythonMidPartActivity.this, PythonMidQuizActivity.class);
            intent.putExtra("part_number", partNumber);
            intent.putExtra("quiz_type", "python_mid");
            startActivity(intent);
        });
    }
}