package io.sikorka.android.ui.accounts.account_import;

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TextInputLayout
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.afollestad.materialdialogs.folderselector.FileChooserDialog
import io.sikorka.android.R
import io.sikorka.android.ui.dialogs.selectFile
import io.sikorka.android.ui.setValue
import io.sikorka.android.ui.value
import toothpick.Scope
import toothpick.Toothpick
import toothpick.smoothie.module.SmoothieActivityModule
import java.io.File
import javax.inject.Inject

class AccountImportActivity : AppCompatActivity(),
    AccountImportView,
    FileChooserDialog.FileCallback {

  @BindView(R.id.account_import__file_passphrase)
  internal lateinit var filePassphraseInput: TextInputLayout

  @BindView(R.id.account_import__account_passphrase)
  internal lateinit var accountPassphraseInput: TextInputLayout

  @BindView(R.id.account_import__account_passphrase_confirmation)
  internal lateinit var accountPassphraseConfirmationInput: TextInputLayout

  @BindView(R.id.account_import__file_path)
  internal lateinit var filePathInput: TextInputLayout


  private val filePassphrase: String
    get() = filePassphraseInput.value()

  private val accountPassphrase: String
    get() = accountPassphraseInput.value()

  private val accountPassphraseConfirmation: String
    get() = accountPassphraseConfirmationInput.value()

  private val filePath: String
    get() = filePathInput.value()


  private lateinit var scope: Scope

  @Inject internal lateinit var presenter: AccountImportPresenter


  @OnClick(R.id.account_import__import_action)
  internal fun onImportPressed() {
    presenter.import(filePath, filePassphrase, accountPassphrase, accountPassphraseConfirmation)
  }

  @OnClick(R.id.account_import__select_file_button)
  internal fun onFileSelectPressed() {
    selectFile()
  }

  override fun onFileChooserDismissed(dialog: FileChooserDialog) {
    // do nothing
  }

  override fun onFileSelection(dialog: FileChooserDialog, file: File) {
    filePathInput.setValue(file.absolutePath)
  }

  override fun showError(code: Long) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun importSuccess() {
    Snackbar.make(filePathInput, R.string.account_import__import_success, Snackbar.LENGTH_SHORT).show()
    finish()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    scope = Toothpick.openScopes(application, this)
    scope.installModules(SmoothieActivityModule(this), AccountImportModule())
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity__account_import)
    Toothpick.inject(this, scope)
    ButterKnife.bind(this)
    supportActionBar?.let {
      it.setDisplayShowHomeEnabled(true)
      it.setDisplayHomeAsUpEnabled(true)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Toothpick.closeScope(this)
  }

  override fun onStart() {
    super.onStart()
    presenter.attach(this)
  }

  override fun onStop() {
    super.onStop()
    presenter.detach()
  }

  override fun onOptionsItemSelected(item: MenuItem?): Boolean {
    return when (item?.itemId) {
      android.R.id.home -> {
        onBackPressed()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  companion object {
    fun start(context: Context) {
      val intent = Intent(context, AccountImportActivity::class.java)
      context.startActivity(intent)
    }
  }
}