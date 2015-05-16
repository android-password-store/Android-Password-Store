package com.zeapo.pwdstore;


import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import com.alexweinert.pwgen.PasswordFactory;
import com.alexweinert.pwgen.SecureRandomGenerator;
import java.util.LinkedList;

public class PasswordGenerator extends ActionBarActivity {


    private PasswordFactory passwordFactory;

    private static final int MUST_POSITION = 0;
    private static final int MAY_POSITION = 1;
    private static final int MUST_NOT_POSITION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passgen);

        this.setupReloadButtonHandler();
        this.populateOptionSpinners();
        this.setupSpinnerHandlers();

        this.updatePasswordFactory();
        this.createAndShowNewPassword();
    }

    private void setupReloadButtonHandler() {
        this.findViewById(R.id.reloadButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                createAndShowNewPassword();
            }
        });

    }

    private void setupSpinnerHandlers() {
        for (Spinner spinner : this.getOptionSpinners()) {
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                    updatePasswordFactory();
                    createAndShowNewPassword();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                    // Does not happen in this context
                }
            });
        }

    }

    private void updatePasswordFactory() {
        PasswordFactory newFactory = this.createPasswordFactory();
        this.passwordFactory = newFactory;
    }

    private PasswordFactory createPasswordFactory() {
        PasswordFactory.Builder factoryBuilder = new PasswordFactory.Builder(new SecureRandomGenerator());
        this.configurePasswordFactoryBuilder(factoryBuilder);
        return factoryBuilder.create();
    }

    private void configurePasswordFactoryBuilder(PasswordFactory.Builder builder) {
        this.configurePasswordFactoryBuilderLowercase(builder);
        this.configurePasswordFactoryBuilderUppercase(builder);
        this.configurePasswordFactoryBuilderDigits(builder);
        this.configurePasswordFactoryBuilderSymbols(builder);
        this.configurePasswordFactoryBuilderAmbiguous(builder);
    }

    private void configurePasswordFactoryBuilderLowercase(PasswordFactory.Builder builder) {
        int position = ((Spinner) this.findViewById(R.id.lowercaseSpinner)).getSelectedItemPosition();
        if (position == MUST_POSITION) {
            builder.mustIncludeLowercase();
        } else if (position == MAY_POSITION) {
            builder.mayIncludeLowercase();
        } else if (position == MUST_NOT_POSITION) {
            builder.mustNotIncludeLowercase();
        }
    }

    private void configurePasswordFactoryBuilderUppercase(PasswordFactory.Builder builder) {
        int position = ((Spinner) this.findViewById(R.id.uppercaseSpinner)).getSelectedItemPosition();
        if (position == MUST_POSITION) {
            builder.mustIncludeUppercase();
        } else if (position == MAY_POSITION) {
            builder.mayIncludeUppercase();
        } else if (position == MUST_NOT_POSITION) {
            builder.mustNotIncludeUppercase();
        }
    }

    private void configurePasswordFactoryBuilderDigits(PasswordFactory.Builder builder) {
        int position = ((Spinner) this.findViewById(R.id.digitsSpinner)).getSelectedItemPosition();
        if (position == MUST_POSITION) {
            builder.mustIncludeDigits();
        } else if (position == MAY_POSITION) {
            builder.mayIncludeDigits();
        } else if (position == MUST_NOT_POSITION) {
            builder.mustNotIncludeDigits();
        }
    }

    private void configurePasswordFactoryBuilderSymbols(PasswordFactory.Builder builder) {
        int position = ((Spinner) this.findViewById(R.id.symbolsSpinner)).getSelectedItemPosition();
        if (position == MUST_POSITION) {
            builder.mustIncludeSymbols();
        } else if (position == MAY_POSITION) {
            builder.mayIncludeSymbols();
        } else if (position == MUST_NOT_POSITION) {
            builder.mustNotIncludeSymbols();
        }
    }

    private void configurePasswordFactoryBuilderAmbiguous(PasswordFactory.Builder builder) {
        int position = ((Spinner) this.findViewById(R.id.ambiguousSpinner)).getSelectedItemPosition();
        if (position == MUST_POSITION) {
            builder.mustIncludeAmbiguous();
        } else if (position == MAY_POSITION) {
            builder.mayIncludeAmbiguous();
        } else if (position == MUST_NOT_POSITION) {
            builder.mustNotIncludeAmbiguous();
        }
    }

    private void createAndShowNewPassword() {
        String password = this.passwordFactory.getPassword(8);
        ((TextView) this.findViewById(R.id.passwordView)).setText(password);
    }


    private void populateOptionSpinners() {
        // Taken from http://developer.android.com/guide/topics/ui/controls/spinner.html
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.optionChoices,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Use the same adapter for all spinners
        for (Spinner spinner : this.getOptionSpinners()) {
            spinner.setAdapter(adapter);
        }
    }

    private Iterable<Spinner> getOptionSpinners() {
        LinkedList<Spinner> returnValue = new LinkedList<Spinner>();
        returnValue.add((Spinner) this.findViewById(R.id.lowercaseSpinner));
        returnValue.add((Spinner) this.findViewById(R.id.uppercaseSpinner));
        returnValue.add((Spinner) this.findViewById(R.id.digitsSpinner));
        returnValue.add((Spinner) this.findViewById(R.id.symbolsSpinner));
        returnValue.add((Spinner) this.findViewById(R.id.ambiguousSpinner));
        return returnValue;
    }
}
