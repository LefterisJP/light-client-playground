package io.sikorka.android.ui.contracts.interact

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import io.sikorka.android.R
import io.sikorka.android.helpers.fail
import io.sikorka.android.ui.gone
import io.sikorka.android.utils.getBitmapFromVectorDrawable
import kotlinx.android.synthetic.main.activity__contract_interact.*
import me.dm7.barcodescanner.zxing.sample.QrScannerActivity
import timber.log.Timber
import toothpick.Scope
import toothpick.Toothpick
import toothpick.smoothie.module.SmoothieSupportActivityModule
import javax.inject.Inject

class ContractInteractActivity : AppCompatActivity(), ContractInteractView {

  @Inject
  lateinit var presenter: ContractInteractPresenter

  private lateinit var scope: Scope

  override fun onCreate(savedInstanceState: Bundle?) {
    scope = Toothpick.openScopes(application, this)
    scope.installModules(SmoothieSupportActivityModule(this), ContractInteractModule())
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity__contract_interact)
    Toothpick.inject(this, scope)

    supportActionBar?.apply {
      setHomeButtonEnabled(true)
      setDisplayHomeAsUpEnabled(true)
      title = ""
    }

    contract_interact__verify.setOnClickListener {
      presenter.verify("")
      QrScannerActivity.start(this)
    }

    contract_interact__contract_address.text = contractAddress

    val map = supportFragmentManager.findFragmentById(R.id.interact_contract__map) as SupportMapFragment
    map.getMapAsync {
      val me = intent?.getParcelableExtra<LatLng>(MY_LOCATION) ?: return@getMapAsync
      val contract = intent?.getParcelableExtra<LatLng>(CONTRACT_LOCATION) ?: return@getMapAsync

      val bitmap = getBitmapFromVectorDrawable(R.drawable.ic_person_pin_circle_black_24dp)
      val icon = BitmapDescriptorFactory.fromBitmap(bitmap)

      val position = CameraPosition.builder()
          .target(me)
          .zoom(10f)
          .bearing(0.0f)
          .tilt(0.0f)
          .build()

      it.addMarker(MarkerOptions().position(me).title("Me").icon(icon))
      it.animateCamera(CameraUpdateFactory.newCameraPosition(position), null)
      it.moveCamera(CameraUpdateFactory.newCameraPosition(position))

      val contracBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_ethereum_icon)
      val contractIcon = BitmapDescriptorFactory.fromBitmap(contracBitmap)

      val markerOptions = MarkerOptions()
          .position(contract)
          .title(contractAddress)
          .icon(contractIcon)

      it.addMarker(markerOptions)
    }
  }

  override fun detector(hex: String) {
    interact_contract__detector_address.text = hex
  }

  override fun onDestroy() {
    super.onDestroy()
    Toothpick.closeScope(this)
  }

  override fun onStart() {
    super.onStart()
    presenter.attach(this)
    presenter.load(contractAddress)
  }

  override fun noDetector() {
    interact_contract__detector_address_group.gone()
  }

  override fun onStop() {
    super.onStop()
    presenter.detach()
  }

  override fun onOptionsItemSelected(item: MenuItem?): Boolean {
    if (item?.itemId == android.R.id.home) {
      onBackPressed()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun showConfirmationResult(confirmAnswer: Boolean) {

  }

  override fun update(name: String) {
    supportActionBar?.title = name
  }

  override fun showError() {
    Snackbar.make(contract_interact__verify, R.string.contract_interact__generic_error, Snackbar.LENGTH_LONG).show()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    Timber.v("result $resultCode")
    if (requestCode == QrScannerActivity.SCANNER_RESULT && resultCode == Activity.RESULT_OK) {
      Timber.v(data?.getStringExtra(QrScannerActivity.DATA))
    }
    super.onActivityResult(requestCode, resultCode, data)
  }


  private val contractAddress: String
    get() = intent?.getStringExtra(CONTRACT_ADDRESS) ?: fail("expected a non null contract address")


  companion object {
    private const val CONTRACT_ADDRESS = "io.sikorka.android.extras.CONTRACT_ADDRESS"
    private const val CONTRACT_LOCATION = "io.sikorka.android.extras.CONTRACT_LOCATION"
    private const val MY_LOCATION = "io.sikorka.android.extras.MY_LOCATION"

    fun start(context: Context, contractAddress: String, me: LatLng, contract: LatLng) {
      val intent = Intent(context, ContractInteractActivity::class.java)
      intent.putExtra(CONTRACT_ADDRESS, contractAddress)
      intent.putExtra(CONTRACT_LOCATION, contract)
      intent.putExtra(MY_LOCATION, me)
      context.startActivity(intent)
    }
  }
}