package com.codelearninapp.pythmaster;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PythonAdvancePartActivity extends AppCompatActivity {

    private int partNumber; // 1..10

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Reuse your existing detail layout or replace with an advanced-specific one
        setContentView(R.layout.activity_advance_python_part1);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        partNumber = getIntent().getIntExtra("part_number", 1);

        Button btnCheckKnowledge = findViewById(R.id.btnCheckKnowledge);
        btnCheckKnowledge.setOnClickListener(v -> {
            Intent intent = new Intent(PythonAdvancePartActivity.this, PythonAdvanceQuizActivity.class);
            intent.putExtra("part_number", partNumber);
            intent.putExtra("quiz_type", "python_advance");
            startActivity(intent);
        });
    }
}