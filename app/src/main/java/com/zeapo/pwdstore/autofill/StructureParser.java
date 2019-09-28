package com.zeapo.pwdstore.autofill;

import android.app.assist.AssistStructure;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillId;

import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;


/**
 * Parse AssistStructure and guess username and password fields.
 */
@RequiresApi(api = Build.VERSION_CODES.O)
class StructureParser {
    static private final String TAG = StructureParser.class.getName();

    final private AssistStructure structure;
    private Result result;

    StructureParser(AssistStructure structure) {
        this.structure = structure;
    }

    Result parse() {
        result = new Result();
        for (int i = 0; i < structure.getWindowNodeCount(); ++i) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            result.title.add(windowNode.getTitle());
            parseViewNode(windowNode.getRootViewNode());
        }
        return result;
    }

    private void parseViewNode(AssistStructure.ViewNode node) {
        String[] hints = node.getAutofillHints();

        if (hints == null) {
            // Could not find native autofill hints.
            // Try to infer any hints from the ID of the field (ie the #id of a webbased text input)
            String inferredHint = inferHint(node, node.getIdEntry());
            if (inferredHint != null) {
                hints = new String[]{inferredHint};
            }
        }

        if (hints != null && hints.length > 0) {
            List<String> hintsAsList = Arrays.asList(hints);

            if (hintsAsList.contains(View.AUTOFILL_HINT_USERNAME)) {
                result.username.add(node.getAutofillId());
            }
            else if (hintsAsList.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS)) {
                result.email.add(node.getAutofillId());
            }
            else if (hintsAsList.contains(View.AUTOFILL_HINT_PASSWORD)) {
                result.password.add(node.getAutofillId());
            }
        } else if (node.getAutofillType() == View.AUTOFILL_TYPE_TEXT) {
            // Attempt to match based on Field Type
            int inputType = node.getInputType();
            switch (inputType) {
                case InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                    result.email.add(node.getAutofillId());
                    break;
                case InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD:
                case InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                    result.password.add(node.getAutofillId());
                    break;
                default:
                    break;
            }
        }

        // Finally look for domain names
        String webDomain = node.getWebDomain();
        if (webDomain != null) {
            result.webDomain.add(webDomain);
        }

        for (int i = 0; i < node.getChildCount(); ++i) {
            parseViewNode(node.getChildAt(i));
        }

    }

    // Attempt to infer the AutoFill type from a string
    private String inferHint(AssistStructure.ViewNode node, @Nullable String actualHint) {
        if (actualHint == null) return null;

        String hint = actualHint.toLowerCase();
        if (hint.contains("label") || hint.contains("container")) {
            return null;
        }

        if (hint.contains("password")) {
            return View.AUTOFILL_HINT_PASSWORD;
        }
        if (hint.contains("username") || (hint.contains("login") && hint.contains("id"))){
            return View.AUTOFILL_HINT_USERNAME;
        }
        if (hint.contains("email")){
            return View.AUTOFILL_HINT_EMAIL_ADDRESS;
        }
        if (hint.contains("name")){
            return View.AUTOFILL_HINT_NAME;
        }
        if (hint.contains("phone")){
            return View.AUTOFILL_HINT_PHONE;
        }

        return null;
    }

    static class Result {
        final List<CharSequence> title;
        final List<String> webDomain;
        final List<AutofillId> username;
        final List<AutofillId> email;
        final List<AutofillId> password;

        private Result() {
            title = new ArrayList<>();
            webDomain = new ArrayList<>();
            username = new ArrayList<>();
            email = new ArrayList<>();
            password = new ArrayList<>();
        }

        public AutofillId[] getAllAutoFillIds() {
            ArrayList<AutofillId> autofillIds = new ArrayList<>();
            autofillIds.addAll(username);
            autofillIds.addAll(email);
            autofillIds.addAll(password);

            AutofillId[] finalAutoFillIds = new AutofillId[autofillIds.size()];
            return autofillIds.toArray(finalAutoFillIds);
        }
    }
}