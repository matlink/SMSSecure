/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import org.smssecure.smssecure.components.AnimatingToggle;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.util.DynamicLanguage;
import org.smssecure.smssecure.util.DynamicNoActionBarTheme;
import org.smssecure.smssecure.util.DynamicTheme;
import org.smssecure.smssecure.util.ServiceUtil;

import java.io.IOException;

/**
 * Base activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class ContactSelectionActivity extends PassphraseRequiredActionBarActivity
                                               implements ContactSelectionListFragment.OnContactSelectedListener
{
  private static final String TAG             = ContactSelectionActivity.class.getSimpleName();
  public  final static String PUSH_ONLY_EXTRA = "push_only";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  protected ContactSelectionListFragment contactsFragment;

  private   Toolbar         toolbar;
  private   EditText        searchText;
  private   AnimatingToggle toggle;
  protected ImageView       action;
  private   ImageView       keyboardToggle;
  private   ImageView       dialpadToggle;
  private   ImageView       clearToggle;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    setContentView(R.layout.contact_selection_activity);

    initializeToolbar();
    initializeResources();
    initializeSearch();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  private void initializeToolbar() {
    this.toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    this.action         = (ImageView) findViewById(R.id.action_icon);
    this.searchText     = (EditText) findViewById(R.id.search_view);
    this.toggle         = (AnimatingToggle) findViewById(R.id.button_toggle);
    this.keyboardToggle = (ImageView) findViewById(R.id.search_keyboard);
    this.dialpadToggle  = (ImageView) findViewById(R.id.search_dialpad);
    this.clearToggle    = (ImageView) findViewById(R.id.search_clear);

    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setOnContactSelectedListener(this);

    this.keyboardToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        ServiceUtil.getInputMethodManager(ContactSelectionActivity.this).showSoftInput(searchText, 0);
        toggle.display(dialpadToggle);
      }
    });

    this.dialpadToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setInputType(InputType.TYPE_CLASS_PHONE);
        ServiceUtil.getInputMethodManager(ContactSelectionActivity.this).showSoftInput(searchText, 0);
        toggle.display(keyboardToggle);
      }
    });

    this.clearToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setText("");

        if (SearchUtil.isTextInput(searchText)) toggle.display(dialpadToggle);
        else                                    toggle.display(keyboardToggle);
      }
    });
  }

  private void initializeSearch() {
    this.searchText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        if      (!SearchUtil.isEmpty(searchText))     toggle.display(clearToggle);
        else if (SearchUtil.isTextInput(searchText))  toggle.display(dialpadToggle);
        else if (SearchUtil.isPhoneInput(searchText)) toggle.display(keyboardToggle);

        contactsFragment.setQueryFilter(searchText.getText().toString());
      }
    });
  }

  @Override
  public void onContactSelected(String number) {}

  private static class SearchUtil {

    public static boolean isTextInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    public static boolean isPhoneInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    public static boolean isEmpty(EditText editText) {
      return editText.getText().length() <= 0;
    }
  }
}
