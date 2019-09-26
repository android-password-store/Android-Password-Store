package com.zeapo.pwdstore.autofill;

import android.app.assist.AssistStructure;
import android.os.Build;

import androidx.annotation.RequiresApi;

import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;

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
    private AutofillId usernameCandidate;

    StructureParser(AssistStructure structure) {
        this.structure = structure;
    }

    Result parse() {
        result = new Result();
        usernameCandidate = null;
        for (int i = 0; i < structure.getWindowNodeCount(); ++i) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            result.title.add(windowNode.getTitle());
            result.webDomain.add(windowNode.getRootViewNode().getWebDomain());
            parseViewNode(windowNode.getRootViewNode());
        }
        // If not explicit username field found, add the field just before password field.
        if (result.username.isEmpty() && result.email.isEmpty() && !result.password.isEmpty() && usernameCandidate != null) {
            result.username.add(usernameCandidate);
        }

        return result;
    }

    private void parseViewNode(AssistStructure.ViewNode node) {
        String[] hints = node.getAutofillHints();
        if (hints != null && hints.length > 0) {
            if (Arrays.asList(hints).contains(View.AUTOFILL_HINT_USERNAME)) {
                result.username.add(node.getAutofillId());
            }
            else if (Arrays.asList(hints).contains(View.AUTOFILL_HINT_EMAIL_ADDRESS)) {
                result.email.add(node.getAutofillId());
            }
            else if (Arrays.asList(hints).contains(View.AUTOFILL_HINT_PASSWORD)) {
                result.password.add(node.getAutofillId());
            }
            else {
                Log.d(TAG, "unsupported hints");
            }
        } else if (node.getAutofillType() == View.AUTOFILL_TYPE_TEXT) {
            int inputType = node.getInputType();
            if ((inputType & InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) > 0) {
                result.email.add(node.getAutofillId());
            }
            else if ((inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD) > 0) {
                result.password.add(node.getAutofillId());
            }
            else if (result.password.isEmpty()) {
                usernameCandidate = node.getAutofillId();
            }
        }

        for (int i = 0; i < node.getChildCount(); ++i) {
            parseViewNode(node.getChildAt(i));
        }
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

        Stream<AutofillId> allAutofillIds() {
            return Stream.concat(Stream.concat(username.stream(), email.stream()), password.stream());
        }
    }
}