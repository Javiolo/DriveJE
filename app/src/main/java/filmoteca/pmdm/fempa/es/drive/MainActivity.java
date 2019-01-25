package filmoteca.pmdm.fempa.es.drive;

import android.content.Intent;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.DriveScopes;

import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    static final int RC_SIGN_IN = 2;
    static final int REQUEST_AUTHORIZATION = 100;
    GoogleSignInClient mGoogleSignInClient;
    GoogleSignInAccount account;
    DriveServiceHelper mDriveServiceHelper;
    Button boton;
    TextView ruta;
    TextView nombreFichero;
    TextView contenidoFichero;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("Traza", "onCreate");
        ruta = findViewById(R.id.editRutaCarpeta);
        nombreFichero = findViewById(R.id.editFichero);
        contenidoFichero = findViewById(R.id.editResultado);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        if (mGoogleSignInClient == null) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(new Scope("https://www.googleapis.com/auth/drive.file"))
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            Log.d("Traza", "mGoogleSignInClient == null");
        }

        if (mDriveServiceHelper == null) {
            signIn();
            Log.d("Traza", "mDriveServiceHelper == null y llama signIn()");
        }

        boton = findViewById(R.id.subir);
        boton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new crearGuardarFicheroThread().run();
            }
        });
    }

    public class crearGuardarFicheroThread extends Thread {
        public void run() {
            Task<String> id = mDriveServiceHelper.createFile();

            id.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(MainActivity.this, "Ha fallado", Toast.LENGTH_SHORT).show();
                }
            });

            id.addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(Task<String> task) {
                    mDriveServiceHelper.saveFile(id.getResult(), ruta.getText().toString(), contenidoFichero.getText().toString());
                    Toast.makeText(MainActivity.this, "To do ok", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void signIn() {
        Log.d("Traza", "en signIn()");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_AUTHORIZATION:
                Log.d("Traza", "en onActivityResult(), REQUEST_AUTHORIZATION");
                break;
            case RC_SIGN_IN:
                Log.d("Traza", "en onActivityResult(), RC_SIGN_IN");
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                handleSignInResult(task);
                seleccionarCuentaGoogle();
                break;
        }
    }

    public void seleccionarCuentaGoogle() {
        Log.d("Traza", "en seleccionarCuentaGoogle()");
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account.getAccount());
        com.google.api.services.drive.Drive googleDriveService = new com.google.api.services.drive.Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("DriveJE")
                .build();
        mDriveServiceHelper = new DriveServiceHelper(googleDriveService, this);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            account = completedTask.getResult(ApiException.class);
            Log.d("Traza", "en handleSignInResult(), si llega aqui el account esta ok");
        } catch (ApiException e) {}
    }

    @Override
    protected void onStart() {
        super.onStart();
        account = GoogleSignIn.getLastSignedInAccount(this);
        Log.d("Traza", "en onStart()");
    }
}
