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

public class PythonAdvanceQuizActivity extends AppCompatActivity {

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
    private String quizType = "python_advance";

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
        setContentView(R.layout.activity_quiz); // keep your quiz XML

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
        // Do NOT trim the list; use all questions defined for this part.

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
                    .child("pythonAdvancePartsCompleted")
                    .child("part" + partNumber)
                    .setValue(true);

            // 2) award points atomically (10 per correct)
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

            // 3) log history entry for History screen
            HistoryLogger.logQuizCompletion(
                    userId,
                    "python_advance",
                    partNumber,
                    pointsEarned,
                    correctAnswers,
                    questions.size(),
                    "Python Advance — Part " + partNumber
            );

            // 4) recompute overall progress (10 parts)
            database.getReference("users").child(userId)
                    .child("pythonAdvancePartsCompleted")
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
                            int percent = (int) (completed * 100.0 / 10.0);
                            database.getReference("users").child(userId)
                                    .child("pythonAdvanceProgress")
                                    .setValue(percent);
                        }
                        @Override public void onCancelled(DatabaseError error) {}
                    });
        }
        finish();
    }

    private List<Question> getQuestions(String quizType, int part) {
        List<Question> list = new ArrayList<>();
        if ("python_advance".equals(quizType)) {
            switch (part) {
                case 1: // OOP
                    list.add(new Question("Which feature lets a subclass override a method?", new String[]{"Polymorphism", "Encapsulation", "Abstraction", "Typing"}, 0));
                    list.add(new Question("What does __init__ define?", new String[]{"Constructor", "Destructor", "Operator overload", "Property"}, 0));
                    list.add(new Question("Self in a method refers to?", new String[]{"Instance", "Class", "Module", "Package"}, 0));
                    list.add(new Question("Private-like name uses:", new String[]{"__name", "_name", "name__", "name_"}, 0));
                    break;
                case 2: // Decorators
                    list.add(new Question("A decorator takes and returns a:", new String[]{"Function", "Class", "Module", "String"}, 0));
                    list.add(new Question("@property decorates a:", new String[]{"Getter", "Setter", "Class", "Module"}, 0));
                    list.add(new Question("Wraps metadata of functions:", new String[]{"functools.wraps", "functools.reduce", "itertools.wrap", "contextlib.wrap"}, 0));
                    list.add(new Question("Class decorator receives a:", new String[]{"Class", "Instance", "String", "Module"}, 0));
                    break;
                case 3: // Generators & Iterators
                    list.add(new Question("Keyword to produce a generator:", new String[]{"yield", "return", "gen", "produce"}, 0));
                    list.add(new Question("Protocol to make object iterable:", new String[]{"__iter__", "__get__", "__hash__", "__repr__"}, 0));
                    list.add(new Question("Generator expression syntax:", new String[]{"(x for x in a)", "[x for x in a]", "{x for x in a}", "gen[x for x in a]"}, 0));
                    list.add(new Question("StopIteration signals:", new String[]{"Iteration end", "Error thrown", "Context exit", "Async stop"}, 0));
                    break;
                case 4: // Exceptions
                    list.add(new Question("Always executes:", new String[]{"finally", "else", "catch", "guard"}, 0));
                    list.add(new Question("To raise an error:", new String[]{"raise ValueError()", "throw ValueError()", "error ValueError()", "except ValueError()"}, 0));
                    list.add(new Question("Base for user-defined exceptions:", new String[]{"Exception", "BaseError", "Runtime", "Throwable"}, 0));
                    list.add(new Question("Catch all exceptions:", new String[]{"except Exception:", "catch *:", "except:", "handle all:"}, 0));
                    break;
                case 5: // Context Managers
                    list.add(new Question("Context manager magic methods:", new String[]{"__enter__/__exit__", "__open__/__close__", "__start__/__stop__", "__in__/__out__"}, 0));
                    list.add(new Question("Helper module for contexts:", new String[]{"contextlib", "functools", "itertools", "asyncio"}, 0));
                    list.add(new Question("with open('f') as f uses:", new String[]{"Context manager", "Decorator", "Generator", "Coroutine"}, 0));
                    list.add(new Question("Exit method gets exception info:", new String[]{"type, value, traceback", "code, message", "errno, msg", "name, line"}, 0));
                    break;
                case 6: // Files & Serialization
                    list.add(new Question("Serialize Python obj to JSON:", new String[]{"json.dump", "pickle.save", "csv.write", "yaml.dump"}, 0));
                    list.add(new Question("Binary protocol serializer:", new String[]{"pickle", "json", "csv", "sqlite"}, 0));
                    list.add(new Question("Pathlib class to represent paths:", new String[]{"Path", "File", "Dir", "Fs"}, 0));
                    list.add(new Question("CSV module writer:", new String[]{"csv.writer", "csv.dump", "csv.save", "csv.out"}, 0));
                    break;
                case 7: // Concurrency
                    list.add(new Question("Async IO framework:", new String[]{"asyncio", "threading", "multiprocessing", "select"}, 0));
                    list.add(new Question("Thread-safe lock:", new String[]{"Lock", "Fence", "Barrier", "Gate"}, 0));
                    list.add(new Question("Run CPU-bound in parallel:", new String[]{"multiprocessing", "asyncio", "threading", "selectors"}, 0));
                    list.add(new Question("Await requires:", new String[]{"coroutine", "generator", "iterator", "context"}, 0));
                    break;
                case 8: // Modules & Packaging
                    list.add(new Question("Create venv (py3):", new String[]{"python -m venv venv", "pip venv", "virtualenv()", "py venv"}, 0));
                    list.add(new Question("Install deps from file:", new String[]{"pip install -r requirements.txt", "pip load deps.txt", "pip add packages", "pip apply list"}, 0));
                    list.add(new Question("Package metadata file (pyproject):", new String[]{"pyproject.toml", "setup.cfg", "project.json", "package.toml"}, 0));
                    list.add(new Question("Build wheels with:", new String[]{"pip wheel / build backend", "pip pack", "python build.py", "pip export"}, 0));
                    break;
                case 9: // Testing & Debugging
                    list.add(new Question("Popular test framework:", new String[]{"pytest", "junit", "mocha", "rspec"}, 0));
                    list.add(new Question("Start debugger:", new String[]{"pdb.set_trace()", "debug()", "break()", "inspect()"}, 0));
                    list.add(new Question("Run tests in pytest:", new String[]{"pytest", "python -m tests", "pytests", "runtests"}, 0));
                    list.add(new Question("Measure coverage tool:", new String[]{"coverage.py", "pytest-only", "unitcover", "covr"}, 0));
                    break;
                case 10: // Performance & Profiling
                    list.add(new Question("Time profiler:", new String[]{"cProfile", "timeit", "perf", "lineprof"}, 0));
                    list.add(new Question("LRU cache decorator:", new String[]{"@lru_cache", "@cache_lru", "@memoize", "@lru"}, 0));
                    list.add(new Question("Vectorization often uses:", new String[]{"NumPy", "Tkinter", "Flask", "Regex"}, 0));
                    list.add(new Question("Big-O for binary search:", new String[]{"O(log n)", "O(n)", "O(1)", "O(n log n)"}, 0));
                    break;
                default:
                    list.add(new Question("Which feature lets a subclass override a method?", new String[]{"Polymorphism", "Encapsulation", "Abstraction", "Typing"}, 0));
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