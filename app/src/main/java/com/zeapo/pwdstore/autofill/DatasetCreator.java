package com.zeapo.pwdstore.autofill;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import com.zeapo.pwdstore.PasswordEntry;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.openintents.openpgp.util.OpenPgpApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TargetApi(26)
public class DatasetCreator {
    Context applicationContext;

    public DatasetCreator(
            Context applicationContext
    ) {
        this.applicationContext = applicationContext;
    }

    private Dataset assembleDatasetFromEntry(
            PasswordEntry entry,
            List<AutofillInfo> fields,
            File file
    ) {
        File repositoryPath = PasswordRepository.getRepositoryDirectory(applicationContext);
        Dataset.Builder datasetBuilder = new Dataset.Builder();
        for (AutofillInfo field : fields) {
            String hint = field.hint;
            AutofillId autofillId = field.autofillId;

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

    private FillResponse assembeAuthPromptResponse(List<AutofillInfo> fields, Intent result) {
        PendingIntent pendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
        IntentSender intentSender = pendingIntent.getIntentSender();
        List<AutofillId> autofillIds = new ArrayList<>();
        FillResponse.Builder fillResponseBuilder = new FillResponse.Builder();


        for (AutofillInfo field : fields) {
            autofillIds.add(field.autofillId);
        }

        String packageName = applicationContext.getPackageName();
        RemoteViews authPresentation =
                new RemoteViews(packageName, R.layout.multidataset_service_list_item);
        authPresentation.setTextViewText(
                R.id.text,
                applicationContext.getString(R.string.autofill_unlock_keychain)
        );
        fillResponseBuilder.setAuthentication(
                autofillIds.toArray(new AutofillId[autofillIds.size()]),
                intentSender,
                authPresentation
        );
        return fillResponseBuilder.build();
    }

    public void createOneDataset(
            final File match,
            final List<AutofillInfo> fields,
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
                        FillResponse authResponse = assembeAuthPromptResponse(fields, result);
                        datasetCreationListener.onAuthenticationRequired(authResponse);
                    }
                }
        );
    }

    public void createDatasetBatch(
            List<File> matches,
            final List<AutofillInfo> fields,
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
                        FillResponse authResponse = assembeAuthPromptResponse(fields, result);
                        datasetCreationListener.onAuthenticationRequired(authResponse);
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
