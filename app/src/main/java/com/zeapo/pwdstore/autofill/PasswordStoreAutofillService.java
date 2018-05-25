/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zeapo.pwdstore.autofill;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillId;
import android.widget.Toast;

import com.zeapo.pwdstore.PasswordEntry;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PasswordStoreAutofillService extends AutofillService {
    private OpenPgpServiceConnection serviceConnection;
    private static final String TAG = "PasswordStoreAutofillService";
    private static final List<String> htmlAttributesWithHints = new ArrayList<String>(Arrays.asList(
            "name", "id", "type"
    ));

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal,
                              final FillCallback callback) {
        Log.d(TAG, "onFillRequest()");
        // Find autofillable fields
        AssistStructure structure = getLatestAssistStructure(request);
        Map<String, AutofillInfo> fields = getAutofillableFields(structure);

        final List<AutofillId> autofillIds = new ArrayList<>();

        Log.d(TAG, "autofillable fields:" + fields);

        if (fields.isEmpty()) {
            toast("No autofill hints found");
            callback.onSuccess(null);
            return;
        }

        // Create the base response
        final FillResponse.Builder response = new FillResponse.Builder();

        DatasetCreator datasetCreator = new DatasetCreator(
                getApplicationContext(),
                fields,
                new OnAllDatasetsCreatedListener() {
                    @Override
                    public void onAllDatasetsCreated(List<Dataset> datasetList) {
                        for (Dataset dataset: datasetList) {
                            response.addDataset(dataset);
                        }
                        callback.onSuccess(response.build());
                    }
                });
        datasetCreator.createAllDatasets();
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        Log.d(TAG, "onSaveRequest()");
        toast("Save not supported");
        callback.onSuccess();
    }

    /**
     * Parses the {@link AssistStructure} representing the activity being autofilled, and returns a
     * map of autofillable fields (represented by their autofill ids) mapped by the hint associate
     * with them.
     * <p>
     * <p>An autofillable field is a {@link ViewNode} whose {@link #getHint(ViewNode)} metho
     */
    @NonNull
    private Map<String, AutofillInfo> getAutofillableFields(@NonNull AssistStructure structure) {
        ArrayMap<String, AutofillInfo> fields = new ArrayMap<>();
        int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            ViewNode node = structure.getWindowNodeAt(i).getRootViewNode();
            addAutofillableFields(fields, node, node.getWebDomain());
        }
        return fields;
    }


    /**
     * Adds any autofillable view from the {@link ViewNode} and its descendants to the map.
     */
    private void addAutofillableFields(@NonNull Map<String, AutofillInfo> fields,
                                       @NonNull ViewNode node,
                                       String webDomain
    ) {
        // with this we carry over possible webdomais from parent, we need it later to find our
        // password
        webDomain = node.getWebDomain() != null ? node.getWebDomain() : webDomain;
        int type = node.getAutofillType();
        // We're simple, we just autofill text fields.
        if (type == View.AUTOFILL_TYPE_TEXT) {
            // first try to get hint from android field hints
            AutofillId id = node.getAutofillId();
            String hint = getHint(node);
            if (hint != null) {
                fields.put(hint, new AutofillInfo(id, AutofillTypes.ACTIVITY_AUTOFILL, webDomain));
            }

            // try our luck with html attributes
            hint = getHintFromHTML(node);
            if (hint != null) {
                fields.put(hint, new AutofillInfo(id, AutofillTypes.HTML_AUTOFILL, webDomain));
            }

        }
        int childrenSize = node.getChildCount();
        for (int i = 0; i < childrenSize; i++) {
            addAutofillableFields(fields, node.getChildAt(i), webDomain);
        }
    }

    @Nullable
    protected String getHint(@NonNull ViewNode node) {
        String hint = null;
        // First try the explicit autofill hints...
        String[] hints = node.getAutofillHints();
        if (hints != null) {
            // We're simple, we only care about the first hint
            hint = hints[0].toLowerCase();
        }
        if (hint != null) return hint;

        // Then try some rudimentary heuristics based on other node properties
        String viewHint = node.getHint();
        hint = inferHint(viewHint);
        if (hint != null) {
            Log.d(TAG, "Found hint using view hint(" + viewHint + "): " + hint);
            return hint;
        } else if (!TextUtils.isEmpty(viewHint)) {
            Log.v(TAG, "No hint using view hint: " + viewHint);
        }
        String resourceId = node.getIdEntry();
        hint = inferHint(resourceId);
        if (hint != null) {
            Log.d(TAG, "Found hint using resourceId(" + resourceId + "): " + hint);
            return hint;
        } else if (!TextUtils.isEmpty(resourceId)) {
            Log.v(TAG, "No hint using resourceId: " + resourceId);
        }

        CharSequence text = node.getText();
        CharSequence className = node.getClassName();
        if (text != null && className != null && className.toString().contains("EditText")) {
            hint = inferHint(text.toString());
            if (hint != null) {
                // NODE: text should not be logged, as it could contain PII
                Log.d(TAG, "Found hint using text(" + text + "): " + hint);
                return hint;
            }
        } else if (!TextUtils.isEmpty(text)) {
            // NODE: text should not be logged, as it could contain PII
            Log.v(TAG, "No hint using text: " + text + " and class " + className);
        }

        return null;
    }

    protected String getHintFromHTML(ViewNode node) {
        String hint = null;
        // finally for webstuff get creative and read attributes
        ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null) {
            List<Pair<String, String>> htmlAttributes = htmlInfo.getAttributes();
            for (Pair<String, String> attribute : htmlAttributes) {
                if (htmlAttributesWithHints.contains(attribute.first)) {
                    hint = inferHint(attribute.second);
                    if (hint != null) {
                        Log.d(TAG, "Found hint using html attribute(" + attribute + "): " + hint);
                        return hint;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Uses heuristics to infer an autofill hint from a {@code string}.
     *
     * @return standard autofill hint, or {@code null} when it could not be inferred.
     */
    @Nullable
    protected String inferHint(@Nullable String string) {
        if (string == null) return null;

        string = string.toLowerCase();
        if (string.contains("password")) return View.AUTOFILL_HINT_PASSWORD;
        if (string.contains("username") || string.contains("login"))
            return View.AUTOFILL_HINT_USERNAME;
        if (string.contains("email")) return View.AUTOFILL_HINT_EMAIL_ADDRESS;
        if (string.contains("name")) return View.AUTOFILL_HINT_NAME;
        return null;
    }

    /**
     * Helper method to get the {@link AssistStructure} associated with the latest request
     * in an autofill context.
     */
    @NonNull
    private static AssistStructure getLatestAssistStructure(@NonNull FillRequest request) {
        List<FillContext> fillContexts = request.getFillContexts();
        return fillContexts.get(fillContexts.size() - 1).getStructure();
    }






    /**
     * Displays a toast with the given message.
     */
    private void toast(@NonNull CharSequence message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }
}
