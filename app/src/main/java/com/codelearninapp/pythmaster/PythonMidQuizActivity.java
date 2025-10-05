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

public class PythonMidQuizActivity extends AppCompatActivity {

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
    private String quizType = "python_mid";

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
        setContentView(R.layout.activity_quiz); // keep your same quiz XML/design

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
        // Do NOT trim; use all questions defined.

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
            // 1) mark part complete
            database.getReference("users").child(userId)
                    .child("pythonMidPartsCompleted")
                    .child("part" + partNumber)
                    .setValue(true);

            // 2) award points
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

            // 3) log history
            HistoryLogger.logQuizCompletion(
                    userId,
                    "python_mid",
                    partNumber,
                    pointsEarned,
                    correctAnswers,
                    questions.size(),
                    "Mid Level — Part " + partNumber
            );

            // 4) recompute mid progress (5 parts)
            database.getReference("users").child(userId)
                    .child("pythonMidPartsCompleted")
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
                                    .child("pythonMidProgress")
                                    .setValue(percent);
                        }
                        @Override public void onCancelled(DatabaseError error) {}
                    });
        }
        finish();
    }

    private List<Question> getQuestions(String quizType, int part) {
        List<Question> list = new ArrayList<>();
        if ("python_mid".equals(quizType)) {
            switch (part) {
                case 1:
                    list.add(new Question("Which built-in data type is mutable?",
                            new String[]{"list", "tuple", "str", "int"}, 0));
                    list.add(new Question("Which method adds an element to a list?",
                            new String[]{"append()", "add()", "push()", "insertAtEnd()"}, 0));
                    list.add(new Question("Which collection is key-value?",
                            new String[]{"dict", "list", "tuple", "set"}, 0));
                    list.add(new Question("Which creates a set?",
                            new String[]{"{1,2,3}", "[1,2,3]", "(1,2,3)", "{(1,2), (3,4)}"}, 0));
                    break;
                case 2:
                    list.add(new Question("Which slicing returns first three items of list a?",
                            new String[]{"a[:3]", "a[0;3]", "a[1..3]", "slice(a,3)"}, 0));
                    list.add(new Question("Which creates a tuple?",
                            new String[]{"(1,2,3)", "[1,2,3]", "{1,2,3}", "tuple[1,2,3]"}, 0));
                    list.add(new Question("How to get length of s?",
                            new String[]{"len(s)", "length(s)", "size(s)", "s.len"}, 0));
                    list.add(new Question("Which merges dictionaries d1 and d2 in 3.9+?",
                            new String[]{"d1 | d2", "merge(d1,d2)", "d1+d2", "dict(d1,d2)"}, 0));
                    break;
                case 3:
                    list.add(new Question("What’s list comprehension doing: [x*x for x in a]?",
                            new String[]{"creates list of squares", "filters even numbers", "sorts list", "maps to strings"}, 0));
                    list.add(new Question("Which comprehension filters even numbers from a?",
                            new String[]{"[x for x in a if x%2==0]", "[x if x%2==0 for x in a]", "{x for x in a if even(x)}", "(x for x in a if even(x))"}, 0));
                    list.add(new Question("Which is a generator?",
                            new String[]{"(x*x for x in a)", "[x*x for x in a]", "{x*x for x in a}", "gen[x*x for x in a]"}, 0));
                    list.add(new Question("Function that yields values is called a?",
                            new String[]{"generator function", "coroutine", "iterator class", "context manager"}, 0));
                    break;
                case 4:
                    list.add(new Question("How to handle exception?",
                            new String[]{"try/except", "if/else", "guard/catch", "throw/handle"}, 0));
                    list.add(new Question("Which raises an exception?",
                            new String[]{"raise ValueError()", "throw ValueError()", "except ValueError()", "ValueError.raise()"}, 0));
                    list.add(new Question("Finally block executes when?",
                            new String[]{"always after try/except", "only on error", "never", "only on success"}, 0));
                    list.add(new Question("Custom exception class must inherit from?",
                            new String[]{"Exception", "Error", "Throwable", "BaseError"}, 0));
                    break;
                case 5:
                    list.add(new Question("How to import foo from package pkg?",
                            new String[]{"from pkg import foo", "pkg->foo", "import foo from pkg", "pkg.import(foo)"}, 0));
                    list.add(new Question("Which shows installed packages (pip)?",
                            new String[]{"pip list", "pip show", "pip info", "pip modules"}, 0));
                    list.add(new Question("Which creates a virtual environment (py3)?",
                            new String[]{"python -m venv venv", "pip venv", "virtualenv new", "python venv"}, 0));
                    list.add(new Question("Which file lists deps for pip?",
                            new String[]{"requirements.txt", "packages.txt", "deps.txt", "pip.txt"}, 0));
                    break;
                default:
                    list.add(new Question("Which built-in data type is mutable?",
                            new String[]{"list", "tuple", "str", "int"}, 0));
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