package com.codelearninapp.pythmaster;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PythonBasicQuizActivity extends AppCompatActivity {
    private TextView tvQuizProgress, tvQuizQuestion;
    private CardView[] cardOptions = new CardView[4];
    private TextView[] tvOptions = new TextView[4];
    private AppCompatButton btnNextQuiz;

    private List<Question> questions;
    private int currentQuestion = 0;
    private int correctAnswers = 0;
    private int selectedOption = -1;
    private int displayedCorrectIndex = -1;
    private int partNumber = 1;
    private String quizType = "python_basic";

    private final int COLOR_OPTION_NORMAL_BG = Color.parseColor("#B7BCE8");
    private final int COLOR_OPTION_SELECTED_BG = Color.parseColor("#5C6BC0");
    private final int COLOR_TEXT_NORMAL = Color.parseColor("#222C58");
    private final int COLOR_TEXT_SELECTED = Color.WHITE;

    private String userId;
    private FirebaseDatabase database;

    public static class Question {
        String question;
        String[] options;
        int correctIndex;
        public Question(String question, String[] options, int correctIndex) {
            this.question = question;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        tvQuizProgress = findViewById(R.id.tvQuizProgress);
        tvQuizQuestion = findViewById(R.id.tvQuizQuestion);
        cardOptions[0] = findViewById(R.id.cardQOption1);
        cardOptions[1] = findViewById(R.id.cardQOption2);
        cardOptions[2] = findViewById(R.id.cardQOption3);
        cardOptions[3] = findViewById(R.id.cardQOption4);
        tvOptions[0] = findViewById(R.id.tvQOption1);
        tvOptions[1] = findViewById(R.id.tvQOption2);
        tvOptions[2] = findViewById(R.id.tvQOption3);
        tvOptions[3] = findViewById(R.id.tvQOption4);
        btnNextQuiz = findViewById(R.id.btnNextQuiz);

        partNumber = getIntent().getIntExtra("part_number", 1);
        String extraType = getIntent().getStringExtra("quiz_type");
        if (extraType != null) quizType = extraType;

        userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "guest";
        database = FirebaseDatabase.getInstance();

        for (int i = 0; i < 4; i++) {
            cardOptions[i].setCardElevation(0f);
            cardOptions[i].setUseCompatPadding(false);
        }

        questions = getQuestions(quizType, partNumber);
        Collections.shuffle(questions);
        if (questions.size() > 5) questions = questions.subList(0, 5);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            cardOptions[i].setOnClickListener(v -> selectOption(idx));
        }

        btnNextQuiz.setOnClickListener(v -> {
            if (selectedOption == -1) return;
            saveUserQuestionProgress(currentQuestion, selectedOption == displayedCorrectIndex);
            if (selectedOption == displayedCorrectIndex) correctAnswers++;
            currentQuestion++;
            if (currentQuestion < questions.size()) {
                selectedOption = -1;
                showQuestion();
            } else {
                showResultAndPersist();
            }
        });

        showQuestion();
    }

    private void showQuestion() {
        Question q = questions.get(currentQuestion);
        tvQuizProgress.setText("Question " + (currentQuestion + 1) + " of " + questions.size());
        tvQuizQuestion.setText(q.question);

        resetOptionStyles();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 4; i++) order.add(i);
        Collections.shuffle(order);

        for (int i = 0; i < 4; i++) {
            int optionIdx = order.get(i);
            tvOptions[i].setText(q.options[optionIdx]);
            if (optionIdx == q.correctIndex) displayedCorrectIndex = i;
        }
        selectedOption = -1;
    }

    private void resetOptionStyles() {
        for (int i = 0; i < 4; i++) {
            cardOptions[i].setCardBackgroundColor(COLOR_OPTION_NORMAL_BG);
            tvOptions[i].setTextColor(COLOR_TEXT_NORMAL);
        }
    }

    private void selectOption(int idx) {
        resetOptionStyles();
        cardOptions[idx].setCardBackgroundColor(COLOR_OPTION_SELECTED_BG);
        tvOptions[idx].setTextColor(COLOR_TEXT_SELECTED);
        selectedOption = idx;
    }

    private void showResultAndPersist() {
        int percent = (int) (correctAnswers * 100.0 / questions.size());
        new AlertDialog.Builder(this)
                .setTitle("Result")
                .setMessage("You scored " + correctAnswers + "/" + questions.size() + "\n"
                        + "Your knowledge is " + percent + "% for Part " + partNumber + ".")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> markPartCompleteAndFinish())
                .show();
    }

    private void markPartCompleteAndFinish() {
        if (!userId.equals("guest")) {
            // 1) Mark the part as completed with a clean boolean
            database.getReference("users").child(userId)
                    .child("pythonBasicPartsCompleted")
                    .child("part" + partNumber)
                    .setValue(true);

            // 2) Add points atomically (10 per correct answer)
            final int pointsEarned = correctAnswers * 10;
            database.getReference("users").child(userId)
                    .child("points")
                    .runTransaction(new Transaction.Handler() {
                        @Override
                        public Transaction.Result doTransaction(com.google.firebase.database.MutableData currentData) {
                            Integer current = currentData.getValue(Integer.class);
                            if (current == null) current = 0;
                            currentData.setValue(current + pointsEarned);
                            return Transaction.success(currentData);
                        }
                        @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {}
                    });

            // 3) Write a history entry
            HistoryLogger.logQuizCompletion(
                    userId,
                    "python_basic",
                    partNumber,
                    pointsEarned,
                    correctAnswers,
                    questions.size(),
                    "Python Basic — Part " + partNumber
            );

            // 4) Recompute and store overall basic progress (0..100)
            database.getReference("users").child(userId)
                    .child("pythonBasicPartsCompleted")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            int completed = 0;
                            for (DataSnapshot part : snapshot.getChildren()) {
                                Object v = part.getValue();
                                if (v instanceof Boolean && (Boolean) v) completed++;
                                else if (v instanceof Long && (Long) v > 0) completed++;
                                else if (v instanceof String) {
                                    String s = ((String) v).trim().toLowerCase();
                                    if ("true".equals(s) || "1".equals(s)) completed++;
                                }
                            }
                            int percent = (int) (completed * 100.0 / 5.0);
                            database.getReference("users").child(userId)
                                    .child("pythonBasicProgress")
                                    .setValue(percent);
                        }
                        @Override public void onCancelled(DatabaseError error) {}
                    });
        }
        finish(); // Parent activities refresh progress in onResume()
    }

    private List<Question> getQuestions(String quizType, int part) {
        List<Question> list = new ArrayList<>();
        if ("python_basic".equals(quizType)) {
            switch (part) {
                case 1:
                    list.add(new Question("What is a variable in Python?",
                            new String[]{"A named storage for information", "A keyword", "A function", "A class"}, 0));
                    list.add(new Question("Which is a valid variable name in Python?",
                            new String[]{"my_var", "2var", "var-name", "my var"}, 0));
                    list.add(new Question("Which statement assigns a value to a variable?",
                            new String[]{"x = 5", "x == 5", "let x = 5", "int x = 5"}, 0));
                    list.add(new Question("What will be the output? x = 10; print(x)",
                            new String[]{"10", "x", "Error", "None"}, 0));
                    break;
                case 2:
                    list.add(new Question("Which of the following is a string in Python?",
                            new String[]{"'hello'", "123", "True", "[1,2,3]"}, 0));
                    list.add(new Question("Which is a float?",
                            new String[]{"3.14", "'3.14'", "3", "True"}, 0));
                    list.add(new Question("Which data type is used to store True or False?",
                            new String[]{"bool", "int", "str", "list"}, 0));
                    list.add(new Question("Which is a list?",
                            new String[]{"[1,2,3]", "{1,2,3}", "(1,2,3)", "'1,2,3'"}, 0));
                    break;
                case 3:
                    list.add(new Question("Which function is used for input in Python?",
                            new String[]{"input()", "read()", "scan()", "get()"}, 0));
                    list.add(new Question("Which function prints output in Python?",
                            new String[]{"print()", "output()", "display()", "show()"}, 0));
                    list.add(new Question("What is the output of: print('Hello ' + 'World')?",
                            new String[]{"Hello World", "Hello", "World", "Error"}, 0));
                    list.add(new Question("What does input() return?",
                            new String[]{"A string", "An integer", "A float", "A boolean"}, 0));
                    break;
                case 4:
                    list.add(new Question("Which of the following is an arithmetic operator?",
                            new String[]{"+", "and", "not", "if"}, 0));
                    list.add(new Question("What is the result of 3 * 2?",
                            new String[]{"6", "5", "1", "8"}, 0));
                    list.add(new Question("Which operator is used for exponentiation?",
                            new String[]{"**", "^", "%", "//"}, 0));
                    list.add(new Question("Which operator checks equality?",
                            new String[]{"==", "=", "!=", "<"}, 0));
                    break;
                case 5:
                    list.add(new Question("Which keyword starts a conditional statement?",
                            new String[]{"if", "for", "def", "class"}, 0));
                    list.add(new Question("What does elif mean?",
                            new String[]{"else if", "end if", "else", "if"}, 0));
                    list.add(new Question("Which is the correct syntax?",
                            new String[]{"if x > 0:", "if x > 0 then", "if (x > 0)", "if x > 0"}, 0));
                    list.add(new Question("Which block runs if all conditions are false?",
                            new String[]{"else", "if", "elif", "for"}, 0));
                    break;
                default:
                    list.add(new Question("What is a variable?",
                            new String[]{"A named storage for information", "A keyword", "A function", "A class"}, 0));
            }
        }
        return list;
    }

    private void saveUserQuestionProgress(int questionIndex, boolean isCorrect) {
        if (userId.equals("guest")) return;
        String path = "users/" + userId + "/" + quizType + "/part" + partNumber + "/question" + (questionIndex + 1);
        database.getReference(path).setValue(isCorrect);
    }
}