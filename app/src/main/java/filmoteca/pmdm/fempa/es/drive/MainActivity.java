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
        }

        if (mDriveServiceHelper == null) {
            signIn();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.personalizado, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem menu) {
        switch (menu.getItemId()) {
            case R.id.añadir:
                if (ruta.getText().toString().length() > 0) {
                    buscarCarpetaYCrearFichero();
                } else {
                    Toast.makeText(this, "Tienes que poner el nombre de la carpeta.", Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.bajar:
                contenidoFichero.setText("");
                new leerFicheroThread().run();
                return true;
        }
        return false;
    }

    public class leerFicheroThread extends Thread {
        public void run() {
            Task<GoogleDriveFileHolder> ficheroEncontrado = mDriveServiceHelper.searchFile(nombreFichero.getText().toString(), TYPE_PLAIN_TEXT);

            ficheroEncontrado.addOnCompleteListener(new OnCompleteListener<GoogleDriveFileHolder>() {
                @Override
                public void onComplete(Task<GoogleDriveFileHolder> task) {
                    Task<android.support.v4.util.Pair<String, String>> fichero = mDriveServiceHelper.readFile(task.getResult().getId());

                    fichero.addOnCompleteListener(new OnCompleteListener<android.support.v4.util.Pair<String, String>>() {
                        @Override
                        public void onComplete(Task<android.support.v4.util.Pair<String, String>> task2) {
                            if (task2.getResult() != null) {
                                contenidoFichero.setText(task2.getResult().second.toString());
                                Toast.makeText(MainActivity.this, "Descarga completa.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Error al descargar.", Toast.LENGTH_SHORT).show();
                            }

                        }
                    });

                    fichero.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Toast.makeText(MainActivity.this, "Error al descargar.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    public class buscarCarpetaThread extends Thread {
        public void run() {
            Task<GoogleDriveFileHolder> carpetaTask = mDriveServiceHelper.searchFolder(ruta.getText().toString());

            carpetaTask.addOnCompleteListener(new OnCompleteListener<GoogleDriveFileHolder>() {
                @Override
                public void onComplete(Task<GoogleDriveFileHolder> task) {
                    //Log.d("TrazaCarpeta", "ThreadBuscar ha acabado");

                    if (carpetaTask.getResult().getId() == null) {
                        Task<GoogleDriveFileHolder> carpetaCreada = mDriveServiceHelper.createFolder(ruta.getText().toString(), null);
                        carpetaCreada.addOnCompleteListener(new OnCompleteListener<GoogleDriveFileHolder>() {
                            @Override
                            public void onComplete(@NonNull Task<GoogleDriveFileHolder> task) {
                                carpeta = task.getResult();

                                if(task.isComplete()) {
                                    new crearFicheroThread().run();
                                } else {
                                }
                            }
                        });
                    } else {
                        carpeta = carpetaTask.getResult();
                    }

                    if(carpetaTask.isComplete() && carpeta != null) {
                        new crearFicheroThread().run();
                    } else {
                    }
                }
            });
        }
    }

    public void buscarCarpetaYCrearFichero() {
        buscarCarpetaThread threadBuscar = new buscarCarpetaThread();
        if (ruta.getText().toString().length() > 0) {
            threadBuscar.run();
            try {
                threadBuscar.join();
            } catch (Exception e) {}
        }
    }

    public class crearFicheroThread extends Thread {
        public void run() {
            Task<String> id;

            if (carpeta != null) {
                id = mDriveServiceHelper.createFile(carpeta.getId());
            } else {
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
                    Toast.makeText(MainActivity.this, "Archivo creado correctamente.", Toast.LENGTH_SHORT).show();
                    carpeta = null;
                }
            });
        }
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_AUTHORIZATION:
                break;
            case RC_SIGN_IN:
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                handleSignInResult(task);
                seleccionarCuentaGoogle();
                break;
        }
    }

    public void seleccionarCuentaGoogle() {
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
        } catch (ApiException e) {}
    }

    @Override
    protected void onStart() {
        super.onStart();
        account = GoogleSignIn.getLastSignedInAccount(this);
    }
}
