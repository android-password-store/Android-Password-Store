package com.zeapo.pwdstore.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.service.autofill.Dataset;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import com.zeapo.pwdstore.PasswordEntry;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.crypto.PgpActivity;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.openintents.openpgp.util.OpenPgpApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatasetCreator {
    Context applicationContext;

    public DatasetCreator(
            Context applicationContext
    ) {
        this.applicationContext = applicationContext;
    }

    private Dataset assembleDatasetFromEntry(
            PasswordEntry entry,
            Map<String, AutofillInfo> fields,
            File file
    ) {
        File repositoryPath = PasswordRepository.getRepositoryDirectory(applicationContext);
        Dataset.Builder datasetBuilder = new Dataset.Builder();
        for (Map.Entry<String, AutofillInfo> field : fields.entrySet()) {
            String hint = field.getKey();
            AutofillId autofillId = field.getValue().autofillId;

            String value = hint == View.AUTOFILL_HINT_PASSWORD
                    ? entry.getPassword()
                    : entry.getUsername();

            String displayValue = repositoryPath.toURI().relativize(
                    file.toURI()
            ).getPath();

            RemoteViews presentation = createDatasetPresentation(displayValue);
            datasetBuilder
                    .setValue(
                            autofillId,
                            AutofillValue.forText(value),
                            presentation
                    );
        }
        return datasetBuilder.build();
    }

    private Dataset assembeAuthPromptDataset(Map<String, AutofillInfo> fields, Intent result) {
        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
        String packageName = applicationContext.getPackageName();
        RemoteViews presentation =
                new RemoteViews(packageName, R.layout.multidataset_service_list_item);
        presentation.setTextViewText(R.id.text, "Unlock pass");
        presentation.setImageViewResource(R.id.icon, R.mipmap.ic_launcher);
        Dataset.Builder datasetBuilder = new Dataset.Builder();
        for (Map.Entry<String, AutofillInfo> field : fields.entrySet()) {
            AutofillId autofillId = field.getValue().autofillId;
            datasetBuilder.setValue(
                    autofillId,
                    AutofillValue.forText(""),
                    presentation
            );
        }
        return datasetBuilder.build();
    }

    public void createOneDataset(
            final File match,
            final Map<String, AutofillInfo> fields,
            final DatasetCreationListener datasetCreationListener
    ) {
        Decrypter decrypter = new Decrypter(applicationContext);

        decrypter.bindAndDecrypt(
                match,
                new DecryptionListener() {
                    @Override
                    void onDecrypted(PasswordEntry entry) {
                        Dataset dataset = assembleDatasetFromEntry(entry, fields, match);
                        datasetCreationListener.onDatasetCreated(dataset);
                    }

                    @Override
                    void onAuthenticationRequired(List<File> failedItems, Intent result) {
                        Dataset authPromptDataset = assembeAuthPromptDataset(fields, result);
                        datasetCreationListener.onDatasetCreated(authPromptDataset);
                    }
                }
        );
    }

    public void createDatasetBatch(
            List<File> matches,
            final Map<String, AutofillInfo> fields,
            final DatasetCreationListener datasetCreationListener
    ) {
        Decrypter decrypter = new Decrypter(applicationContext);
        decrypter.bindAndDecryptBatch(
                matches,
                new DecryptionListener() {
                    @Override
                    void onBatchDecrypted(Map<File, PasswordEntry> entries) {
                        List<Dataset> results = new ArrayList<>();
                        for (Map.Entry<File, PasswordEntry> entry : entries.entrySet()) {
                            results.add(
                                    assembleDatasetFromEntry(
                                            entry.getValue(), fields, entry.getKey()
                                    )
                            );
                        }
                        datasetCreationListener.onDatasetBatchCreated(results);
                    }

                    @Override
                    void onAuthenticationRequired(List<File> failedItems, Intent result) {
                        List<Dataset> results = new ArrayList<>();
                        PendingIntent pendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        IntentSender intentSender = pendingIntent.getIntentSender();

                        for (Map.Entry<String, AutofillInfo> field : fields.entrySet()) {
                            Dataset.Builder datasetBuilder = new Dataset.Builder();
                            AutofillId autofillId = field.getValue().autofillId;

                            String packageName = applicationContext.getPackageName();
                            RemoteViews presentation =
                                    new RemoteViews(packageName, R.layout.multidataset_service_list_item);
                            presentation.setTextViewText(
                                    R.id.text,
                                    applicationContext.getString(R.string.autofill_unlock_keychain)
                            );
                            datasetBuilder.setAuthentication(intentSender);
                            datasetBuilder.setValue(
                                    autofillId,
                                    AutofillValue.forText(""),
                                    presentation
                            );
                            results.add(datasetBuilder.build());
                        }
                        datasetCreationListener.onDatasetBatchCreated(results);
                    }
                }
        );
    }

    /**
     * Helper method to create a dataset presentation with the given text.
     */
    @NonNull
    private RemoteViews createDatasetPresentation(@NonNull CharSequence text) {
        String packageName = this.applicationContext.getPackageName();
        RemoteViews presentation =
                new RemoteViews(packageName, R.layout.multidataset_service_list_item);
        presentation.setTextViewText(R.id.text, text);
        presentation.setImageViewResource(R.id.icon, R.mipmap.ic_launcher);
        return presentation;
    }
}
