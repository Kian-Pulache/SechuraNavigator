// archivo: app/src/main/java/com/sechuranavigator/app/ui/AuthActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.sechuranavigator.app.ErrorLogger;
import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.SyncManager;

public class AuthActivity extends AppCompatActivity {

    private EditText    etName, etEmail, etPassword;
    private TextView    tabLogin, tabRegister, btnAuth, btnSkipAuth;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private boolean isLoginMode = true;
    private TextView tvForgotPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // Si ya hay sesión activa, ir directo a la app
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_auth);
        bindViews();
        setupListeners();
    }

    private void bindViews() {
        etName      = findViewById(R.id.etName);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        tabLogin    = findViewById(R.id.tabLogin);
        tabRegister = findViewById(R.id.tabRegister);
        btnAuth     = findViewById(R.id.btnAuth);
        btnSkipAuth = findViewById(R.id.btnSkipAuth);
        progressBar = findViewById(R.id.progressBar);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
    }

    private void setupListeners() {
        tabLogin.setOnClickListener(v -> switchToLogin());
        tabRegister.setOnClickListener(v -> switchToRegister());
        btnAuth.setOnClickListener(v -> handleAuth());
        btnSkipAuth.setOnClickListener(v -> {
            // Guardar que eligió modo local
            getSharedPreferences("sechura_settings", MODE_PRIVATE)
                    .edit()
                    .putBoolean("chose_local_mode", true)
                    .apply();

            startActivity(new Intent(this, HomeActivity.class));
            overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out);
            finish();
        });
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void switchToLogin() {
        isLoginMode = true;
        etName.setVisibility(View.GONE);
        btnAuth.setText("Iniciar sesión");
        tabLogin.setTextColor(android.graphics.Color.parseColor("#FBBF24"));
        tabLogin.setBackgroundColor(
                android.graphics.Color.parseColor("#1AF59E0B"));
        tabRegister.setTextColor(
                android.graphics.Color.parseColor("#6B7280"));
        tabRegister.setBackgroundColor(
                android.graphics.Color.parseColor("#0D1F3C"));
        tvForgotPassword.setVisibility(View.VISIBLE);
    }

    private void switchToRegister() {
        isLoginMode = false;
        etName.setVisibility(View.VISIBLE);
        btnAuth.setText("Crear cuenta");
        tabRegister.setTextColor(
                android.graphics.Color.parseColor("#FBBF24"));
        tabRegister.setBackgroundColor(
                android.graphics.Color.parseColor("#1AF59E0B"));
        tabLogin.setTextColor(android.graphics.Color.parseColor("#6B7280"));
        tabLogin.setBackgroundColor(
                android.graphics.Color.parseColor("#0D1F3C"));
        tvForgotPassword.setVisibility(View.GONE);
    }

    private void handleAuth() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Ingresa email y contraseña",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        if (isLoginMode) {
            login(email, password);
        } else {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Ingresa tu nombre",
                        Toast.LENGTH_SHORT).show();
                setLoading(false);
                return;
            }
            register(name, email, password);
        }
    }

    private void login(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    ErrorLogger.log("Login exitoso: " + email);
                    Toast.makeText(this, "¡Bienvenido!", Toast.LENGTH_SHORT).show();
                    goToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    ErrorLogger.logError("AuthActivity.login", e);
                    String msg = translateAuthError(e.getMessage());
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private void register(String name, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    // Guardar el nombre en el perfil
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        UserProfileChangeRequest profileUpdate =
                                new UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build();
                        user.updateProfile(profileUpdate);
                    }
                    ErrorLogger.log("Registro exitoso: " + email);
                    Toast.makeText(this,
                            "Cuenta creada. ¡Bienvenido " + name + "!",
                            Toast.LENGTH_SHORT).show();
                    goToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    ErrorLogger.logError("AuthActivity.register", e);
                    String msg = translateAuthError(e.getMessage());
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private void goToMain() {
        // Si es un login nuevo (no hay waypoints locales), descargar de la nube
        com.sechuranavigator.app.data.WaypointRepository repo =
                new com.sechuranavigator.app.data.WaypointRepository(this);

        // Verificar si ya hay datos locales
        repo.countAll(count -> {
            if (count == 0) {
                // Sin datos locales → descargar desde Firestore
                new SyncManager(this).downloadAllData((wpCount, trackCount, error) -> {
                    if (error == null && wpCount > 0) {
                        Toast.makeText(this,
                                "✓ " + wpCount + " puntos recuperados de la nube",
                                Toast.LENGTH_LONG).show();
                    }
                    // Ir a la app con o sin datos
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                });
            } else {
                // Ya hay datos locales → ir directo
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnAuth.setEnabled(!loading);
        btnAuth.setAlpha(loading ? 0.5f : 1.0f);
    }

    /**
     * Traduce los mensajes de error de Firebase Auth al español.
     */
    private String translateAuthError(String error) {
        if (error == null) return "Error desconocido";
        if (error.contains("no user record")) return "No existe una cuenta con ese email";
        if (error.contains("password is invalid")) return "Contraseña incorrecta";
        if (error.contains("email address is already in use"))
            return "Ya existe una cuenta con ese email";
        if (error.contains("badly formatted")) return "Email no válido";
        if (error.contains("network error")) return "Sin conexión a internet";
        if (error.contains("too many requests"))
            return "Demasiados intentos. Espera unos minutos";
        return "Error: " + error;
    }

    private void showForgotPasswordDialog() {
        // Campo de email para recuperación
        android.widget.EditText etResetEmail =
                new android.widget.EditText(this);
        etResetEmail.setHint("Tu correo electrónico");
        etResetEmail.setInputType(
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        // Pre-llenar con el email actual si ya lo escribió
        String currentEmail = etEmail.getText().toString().trim();
        if (!currentEmail.isEmpty()) {
            etResetEmail.setText(currentEmail);
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Recuperar contraseña")
                .setMessage("Te enviaremos un enlace para restablecer tu contraseña.")
                .setView(etResetEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String email = etResetEmail.getText().toString().trim();
                    if (email.isEmpty()) {
                        Toast.makeText(this, "Ingresa tu correo",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendPasswordReset(email);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void sendPasswordReset(String email) {
        setLoading(true);
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Correo enviado")
                            .setMessage("Revisa tu bandeja de entrada en " + email +
                                    ". Si no aparece, revisa tu carpeta de spam.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    ErrorLogger.log("Password reset enviado a: " + email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    String msg = e.getMessage() != null &&
                            e.getMessage().contains("no user record")
                            ? "No existe una cuenta con ese correo"
                            : "Error al enviar el correo. Verifica tu conexión.";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    ErrorLogger.logError("sendPasswordReset", e);
                });
    }
}