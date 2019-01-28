package filmoteca.pmdm.fempa.es.drive;


import android.support.v4.util.Pair;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DriveServiceHelper {
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive mDriveService;
    private MainActivity mainActivity;
    public static String TYPE_GOOGLE_DRIVE_FOLDER = "application/vnd.google-apps.folder";


    public DriveServiceHelper(Drive driveService, MainActivity mainActivity) {
        mDriveService = driveService;
        this.mainActivity = mainActivity;
    }

    public Task<String> createFile(String _ruta) {
        return Tasks.call(mExecutor, () -> {
            String ruta = "root";
            if (_ruta.length() >= 1) {
                ruta = _ruta;
            }

            File metadata = new File()
                    .setParents(Collections.singletonList(ruta))
                    .setMimeType("text/plain")
                    .setName("Untitled file");

            File googleFile = null;
            try {
                googleFile = mDriveService.files().create(metadata).execute();
            } catch (UserRecoverableAuthIOException userRecoverableException) {
                Log.d("Traza", "UserRecoverableAuthIOException ha petao y creo intent de permisos drive");
            }

            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            return googleFile.getId();
        });
    }

    public Task<GoogleDriveFileHolder> searchFolder(String folderName) {
        return Tasks.call(mExecutor, () -> {

            // Retrive the metadata as a File object.
            FileList result = mDriveService.files().list()
                    .setQ("mimeType = '" + TYPE_GOOGLE_DRIVE_FOLDER + "' and name = '" + folderName + "' ")
                    .setSpaces("drive")
                    .execute();
            GoogleDriveFileHolder googleDriveFileHolder = new GoogleDriveFileHolder();
            if (result.getFiles().size() > 0) {
                googleDriveFileHolder.setId(result.getFiles().get(0).getId());
                googleDriveFileHolder.setName(result.getFiles().get(0).getName());

            }
            return googleDriveFileHolder;
        });
    }

    public Task<GoogleDriveFileHolder> createFolder(String folderName, String folderId) {
        return Tasks.call(mExecutor, () -> {

            GoogleDriveFileHolder googleDriveFileHolder = new GoogleDriveFileHolder();

            List<String> root;
            if (folderId == null) {
                root = Collections.singletonList("root");
            } else {
                root = Collections.singletonList(folderId);
            }
            File metadata = new File()
                    .setParents(root)
                    .setMimeType(TYPE_GOOGLE_DRIVE_FOLDER)
                    .setName(folderName);

            File googleFile = mDriveService.files().create(metadata).execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }
            googleDriveFileHolder.setId(googleFile.getId());
            return googleDriveFileHolder;
        });
    }

    public Task<Pair<String, String>> readFile(String fileId) {
        return Tasks.call(mExecutor, () -> {
            try {
                //Obtengo los metadatos y los guardo en un objeto File
                File metadata = mDriveService.files().get(fileId).execute();
                String name = metadata.getName();

                //Guardo el contenido en un String
                try (InputStream is = mDriveService.files().get(fileId).executeMediaAsInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    String contents = stringBuilder.toString();

                    return Pair.create(name, contents);
                }
            } catch (Exception e) {
                return null;
            }
        });
    }

    public Task<GoogleDriveFileHolder> searchFile(String fileName, String mimeType) {
        return Tasks.call(mExecutor, () -> {

            FileList result = mDriveService.files().list()
                    .setQ("name = '" + fileName + "' and mimeType ='" + mimeType + "'")
                    .setSpaces("drive")
                    .setFields("files(id, name,size,createdTime,modifiedTime,starred)")
                    .execute();
            GoogleDriveFileHolder googleDriveFileHolder = new GoogleDriveFileHolder();

            if (result.getFiles().size() > 0) {
                googleDriveFileHolder.setId(result.getFiles().get(0).getId());
                googleDriveFileHolder.setName(result.getFiles().get(0).getName());
                googleDriveFileHolder.setModifiedTime(result.getFiles().get(0).getModifiedTime());
                googleDriveFileHolder.setSize(result.getFiles().get(0).getSize());
            }

            return googleDriveFileHolder;
        });
    }

    public Task<Void> saveFile(String fileId, String name, String content) {
        return Tasks.call(mExecutor, () -> {
            //Creo los metadatos del fichero
            File metadata = new File().setName(name);

            //Se convierte el contenido a una instancia de AbstractInputStreamContent
            ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);

            //Se actualizan los datos con lo que nos pasan
            mDriveService.files().update(fileId, metadata, contentStream).execute();
            return null;
        });
    }
}