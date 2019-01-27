package filmoteca.pmdm.fempa.es.drive;

import android.content.Intent;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
    TextView ruta;
    TextView nombreFichero;
    TextView contenidoFichero;
    public static String TYPE_PLAIN_TEXT = "text/plain";
    GoogleDriveFileHolder carpeta;

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
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.personalizado, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem menu) {
        switch (menu.getItemId()) {
            case R.id.añadir:
                buscarCarpetaYCrearFichero();
                return true;
            case R.id.bajar:
                contenidoFichero.setText("");
                new descargarFicheroThread().run();
                return true;
        }
        return false;
    }

    public class descargarFicheroThread extends Thread {
        public void run() {
            Task<GoogleDriveFileHolder> ficheroEncontrado = mDriveServiceHelper.searchFile(nombreFichero.getText().toString(), TYPE_PLAIN_TEXT);

            ficheroEncontrado.addOnCompleteListener(new OnCompleteListener<GoogleDriveFileHolder>() {
                @Override
                public void onComplete(@NonNull Task<GoogleDriveFileHolder> task) {
                    Task<android.support.v4.util.Pair<String, String>> fichero = mDriveServiceHelper.readFile(task.getResult().getId());

                    fichero.addOnCompleteListener(new OnCompleteListener<android.support.v4.util.Pair<String, String>>() {
                        @Override
                        public void onComplete(@NonNull Task<android.support.v4.util.Pair<String, String>> task2) {
                            if (task2.getResult() != null) {
                                contenidoFichero.setText(task2.getResult().second.toString());
                            } else {
                                Toast.makeText(MainActivity.this, "Ha petado la descarga", Toast.LENGTH_SHORT).show();
                            }

                        }
                    });

                    fichero.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(MainActivity.this, "Ha petado la descarga", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    public class buscarCarpetaThread extends Thread {
        public void run() {
            Log.d("TrazaCarpeta", "buscando carpeta llamada " + ruta.getText().toString() );
            Task<GoogleDriveFileHolder> carpetaTask = mDriveServiceHelper.searchFolder(ruta.getText().toString());

            carpetaTask.addOnCompleteListener(new OnCompleteListener<GoogleDriveFileHolder>() {
                @Override
                public void onComplete(Task<GoogleDriveFileHolder> task) {
                    //Log.d("TrazaCarpeta", "ThreadBuscar ha acabado");

                    if (carpetaTask.getResult().getId() == null) {
                        Log.d("TrazaCarpeta", "Se crea la carpeta");
                        Task<GoogleDriveFileHolder> carpetaCreada = mDriveServiceHelper.createFolder(ruta.getText().toString(), null);
                        carpetaCreada.addOnCompleteListener(new OnCompleteListener<GoogleDriveFileHolder>() {
                            @Override
                            public void onComplete(@NonNull Task<GoogleDriveFileHolder> task) {
                                carpeta = task.getResult();
                                Log.d("TrazaCarpeta", "Carpeta creada");

                                if(task.isComplete()) {
                                    Log.d("TrazaCarpeta", "ThreadBuscar va bien");
                                    new crearFicheroThread().run();
                                } else {
                                    Log.d("TrazaCarpeta", "ThreadBuscar se ha adelantado isComplete esta else");
                                }
                            }
                        });
                    } else {
                        carpeta = carpetaTask.getResult();
                    }

                    if(carpetaTask.isComplete() && carpeta != null) {
                        Log.d("TrazaCarpeta", "ThreadBuscar va bien");
                        new crearFicheroThread().run();
                    } else {
                        Log.d("TrazaCarpeta", "ThreadBuscar se ha adelantado isComplete esta else");
                    }
                }
            });
        }
    }

    public void buscarCarpetaYCrearFichero() {
        buscarCarpetaThread threadBuscar = new buscarCarpetaThread();
        if (ruta.getText().toString().length() > 0) {
            Log.d("TrazaCarpeta", "buscando carpeta");
            threadBuscar.run();
            try {
                threadBuscar.join();
                Log.d("TrazaCarpeta", "esperando a....");
            } catch (Exception e) {}
        }
    }

    public class crearFicheroThread extends Thread {
        public void run() {
            //buscarCarpeta();

            Log.d("TrazaCarpeta", "la espera ha acabado");
            Task<String> id;

            if (carpeta != null) {
                Log.d("TrazaCarpeta", "carpeta encontrada con id " + carpeta.getId());
                id = mDriveServiceHelper.createFile(carpeta.getId());
            } else {
                Log.d("TrazaCarpeta", "carpeta NO encontrada");
                id = mDriveServiceHelper.createFile(ruta.getText().toString());
            }

            id.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(MainActivity.this, "Ha fallado", Toast.LENGTH_SHORT).show();
                }
            });

            id.addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(Task<String> task) {
                    mDriveServiceHelper.saveFile(id.getResult(), nombreFichero.getText().toString(), contenidoFichero.getText().toString());
                    Toast.makeText(MainActivity.this, "To do ok", Toast.LENGTH_SHORT).show();
                    carpeta = null;
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
