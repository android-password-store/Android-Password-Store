package com.zeapo.pwdstore.autofill;

import android.content.Context;
import android.service.autofill.Dataset;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import com.zeapo.pwdstore.PasswordEntry;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.utils.PasswordRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatasetCreator {
    Map<String, AutofillInfo> fields;
    OnDatasetCreatedListener onDatasetCreatedListener;
    OnAllDatasetsCreatedListener onAllDatasetsCreatedListener;
    Context applicationContext;
    Integer resultCount = 0;
    List<Dataset> results;


    public DatasetCreator(
            Context applicationContext,
            Map<String, AutofillInfo> fields
    ) {
        this.applicationContext = applicationContext;
        this.fields = fields;
        this.results = new ArrayList<>();
    }

    public DatasetCreator(
            Context applicationContext,
            Map<String, AutofillInfo> fields,
            OnDatasetCreatedListener onDatasetCreatedListener
    ) {
        this(applicationContext, fields);
        this.onDatasetCreatedListener = onDatasetCreatedListener;
    }

    public DatasetCreator(
            Context applicationContext,
            Map<String, AutofillInfo> fields,
            OnAllDatasetsCreatedListener onAllDatasetsCreatedListener
    ) {
        this(applicationContext, fields);
        this.onAllDatasetsCreatedListener = onAllDatasetsCreatedListener;
    }

    public DatasetCreator(
            Context applicationContext,
            Map<String, AutofillInfo> fields,
            OnDatasetCreatedListener onDatasetCreatedListener,
            OnAllDatasetsCreatedListener onAllDatasetsCreatedListener
    ) {
        this(applicationContext, fields, onDatasetCreatedListener);
        this.onAllDatasetsCreatedListener = onAllDatasetsCreatedListener;
    }

    public void onDatasetCreated(Dataset dataset) {
        resultCount++;
        if (onDatasetCreatedListener != null) {
            onDatasetCreatedListener.onDatasetCreated(dataset);
        }
        if (resultCount == fields.entrySet().size()) {
            onAllDatasetsCreated();
        }
    }

    public void onAllDatasetsCreated() {
        if (onAllDatasetsCreatedListener != null) {
            onAllDatasetsCreatedListener.onAllDatasetsCreated(this.results);
        }
    }

    private void createOneDataset(Map.Entry<String, AutofillInfo> field) {
        final Dataset.Builder dataset = new Dataset.Builder();
        final String hint = field.getKey();
        final AutofillId autofillId = field.getValue().autofillId;
        int autofillType = field.getValue().type;

        List<File> matches = new ArrayList<>();

        if (autofillType == AutofillTypes.HTML_AUTOFILL) {
            // Try to get a match via URL
            matches = new ArrayList<>(
                    searchPasswordInRepository(field.getValue().webDomain)
            );
        } else {
            matches = new ArrayList<>();
        }

        if (matches.isEmpty()) {
            resultCount++;
            return;
        }
        DecryptionBatch decryptionBatch = new DecryptionBatch(
                applicationContext,
                matches,
                new OnBatchDecryptedListener(){
                    @Override
                    public void onBatchDecrypted(List<PasswordEntry> passwordEntries) {
                        Dataset.Builder datasetBuilder = new Dataset.Builder();
                        for (PasswordEntry entry : passwordEntries) {
                            String value = hint == View.AUTOFILL_HINT_PASSWORD
                                    ? entry.getPassword()
                                    : entry.getUsername();
                            String displayValue = hint == View.AUTOFILL_HINT_PASSWORD
                                    ? "Password for " + entry.getUsername()
                                    : entry.getUsername();
                            RemoteViews presentation = createDatasetPresentation(displayValue);
                            datasetBuilder.setValue(
                                    autofillId,
                                    AutofillValue.forText(value),
                                    presentation
                            );
                        }
                        Dataset dataset = datasetBuilder.build();
                        results.add(dataset);
                        onDatasetCreated(dataset);
                    }
                });
        decryptionBatch.decryptBatch();
    }

    public void createAllDatasets() {
        for (Map.Entry<String, AutofillInfo> field : fields.entrySet()) {
            createOneDataset(field);
        }
    }

    private ArrayList<File> searchPasswordInRepository(String name) {
        if (name == null) return new ArrayList<>();
        return searchPasswords(PasswordRepository.getRepositoryDirectory(applicationContext), name);
    }

    private ArrayList<File> searchPasswords(File path, String appName) {
        ArrayList<File> passList = PasswordRepository.getFilesList(path);

        if (passList.size() == 0) return new ArrayList<>();

        ArrayList<File> items = new ArrayList<>();

        for (File file : passList) {
            if (file.isFile()) {
                if (appName.toLowerCase().contains(file.getName().toLowerCase().replace(".gpg", ""))) {
                    items.add(file);
                }
            } else {
                // ignore .git and .extensions directory
                if (file.getName().equals(".git") || file.getName().equals(".extensions"))
                    continue;
                items.addAll(searchPasswords(file, appName));
            }
        }
        return items;
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
