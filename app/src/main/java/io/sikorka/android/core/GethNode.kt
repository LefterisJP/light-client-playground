package io.sikorka.android.core

import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.sikorka.android.core.accounts.AccountModel
import io.sikorka.android.core.configuration.ConfigurationProvider
import io.sikorka.android.core.configuration.IConfiguration
import io.sikorka.android.core.contracts.model.ContractGas
import io.sikorka.android.core.ethereumclient.LightClient
import io.sikorka.android.core.ethereumclient.LightClientProvider
import io.sikorka.android.core.model.converters.SikorkaAddressConverter
import io.sikorka.android.data.syncstatus.SyncStatus
import io.sikorka.android.data.syncstatus.SyncStatusProvider
import io.sikorka.android.helpers.fail
import io.sikorka.android.utils.schedulers.SchedulerProvider
import org.ethereum.geth.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class GethNode
@Inject
constructor(
    private val configurationProvider: ConfigurationProvider,
    private val schedulerProvider: SchedulerProvider,
    private val lightClientProvider: LightClientProvider,
    private val syncStatusProvider: SyncStatusProvider
) {
  private val ethContext = Geth.newContext()
  private var node: Node? = null
  private lateinit var configuration: IConfiguration

  private val disposables: CompositeDisposable = CompositeDisposable()
  private val loggingThrottler: PublishRelay<PeerInfos> = PublishRelay.create()
  private val headerRelay: PublishRelay<Header> = PublishRelay.create()
  private val addressConverter = SikorkaAddressConverter()

  private fun addDisposable(disposable: Disposable) {
    disposables.add(disposable)
  }

  fun start() {
    if (node != null) {
      return
    }

    configuration = configurationProvider.getActive()
    val dataDir = configuration.dataDir
    val nodeConfig = configuration.nodeConfig

    Timber.v("node data directory will be in $dataDir")
    node = Geth.newNode(dataDir.absolutePath, nodeConfig)
    val node = node ?: fail("what node?")
    node.start()

    lightClientProvider.initialize(LightClient(node.ethereumClient, ethContext))

    periodicallyCheckIfRunning()
    addDisposable(
        loggingThrottler.throttleLast(1, TimeUnit.MINUTES)
            .subscribe { logPeers(it) }
    )

    addDisposable(headers()
        .subscribeOn(schedulerProvider.io())
        .observeOn(schedulerProvider.io())
        .subscribe({
          headerRelay.accept(it)
        }) {
          Timber.v(it, "Error")
        }
    )
  }

  fun stop() {
    Timber.v("Stoping geth node.")
    try {
      val node = node ?: return
      node.stop()
    } catch (e: Exception) {
      Timber.v(e)
    }
    disposables.clear()
  }


  fun createTransactOpts(
      account: AccountModel,
      gas: ContractGas,
      signer: TransactionSigner): Single<TransactOpts> {
    return Single.fromCallable {
      val signerAddress = Geth.newAddressFromHex(account.addressHex)
      val opts = TransactOpts()
      opts.setContext(ethContext)
      opts.from = signerAddress
      opts.nonce = ethereumClient.getPendingNonceAt(ethContext, signerAddress)
      opts.setSigner({ address, transaction -> signer(address, transaction, chainId()) })
      opts.gasLimit = gas.limit
      opts.gasPrice = Geth.newBigInt(gas.price)
      return@fromCallable opts
    }
  }

  private fun chainId(): BigInt {
    val nodeConfig = configuration.nodeConfig
    return Geth.newBigInt(nodeConfig.ethereumNetworkID)
  }

  fun suggestedGasPrice(): Single<ContractGas> = Single.fromCallable {
    val suggestGasPrice = ethereumClient.suggestGasPrice(ethContext)
    val gasLimit = ethereumClient.getBlockByNumber(ethContext, -1).gasLimit
    Timber.v("suggested gas price ${suggestGasPrice.getString(10)} wei")
    ContractGas(suggestGasPrice.getString(10).toLong(), gasLimit)
  }.timeout(1, TimeUnit.SECONDS).onErrorReturn { ContractGas(0, 0) }

  fun ethereumClient(): Single<EthereumClient> = Single.fromCallable { ethereumClient }

  private val ethereumClient: EthereumClient
    get() {
      checkNodeStatus()
      val ethNode = node ?: fail("no node")
      val ethereumClient = ethNode.ethereumClient
      return ethereumClient ?: fail("no client")
    }

  private fun checkNodeStatus() {
    if (node == null) {
      Timber.v("Node was null")
      start()
    }
  }

  private fun periodicallyCheckIfRunning() {
    addDisposable(Observable.interval(10, TimeUnit.MINUTES)
        .subscribe({
          checkNodeStatus()
        }) {
          Timber.v(it, "")
        })
  }

  private fun syncProgress(ec: EthereumClient): Pair<Long, Long> = try {
    val syncProgress = ec.syncProgress(ethContext)
    val currentBlock = ec.getBlockByNumber(ethContext, -1)
    val highestBlock = syncProgress?.highestBlock ?: currentBlock.number
    Pair(currentBlock.number, highestBlock)
  } catch (ex: Throwable) {
    Timber.v(ex.message?.substring(0, 125))
    Pair(0, 0)
  }

  private fun headers(): Observable<Header> = Observable.create<Header> {
    val handler = object : NewHeadHandler {
      override fun onError(error: String) {
        Timber.v(error)
      }

      override fun onNewHead(header: Header) {
        it.onNext(header)
      }
    }
    ethereumClient.subscribeNewHead(ethContext, handler, 16)
  }

  fun status(): Observable<SyncStatus> = Observable.interval(2, TimeUnit.SECONDS)
      .flatMap { checkStatus() }
      .startWith(checkStatus())


  private fun checkStatus(): Observable<SyncStatus> = Observable.fromCallable {
    val ethNode = node ?: fail("node was null")
    val peers = ethNode.peersInfo
    val peerCount = peers.size().toInt()
    loggingThrottler.accept(peers)
    val (current, highest) = syncProgress(ethNode.ethereumClient)
    val syncStatus = SyncStatus(peerCount > 0, peerCount, current, highest)

    if (syncStatusProvider.value != syncStatus) {
      syncStatusProvider.postValue(syncStatus)
    }

    syncStatus
  }.onErrorReturn { SyncStatus() }

  private fun logPeers(peerInfos: PeerInfos?) {
    if (peerInfos == null) {
      return
    }
    Timber.v("Printing connected Peers\n========")
    peerInfos.all().forEach {
      Timber.v("Client => ${it.name} -- \"enode://${it.id}@${it.remoteAddress}\"")
    }
    Timber.v("========")
  }

}

typealias TransactionSigner = (address: Address, transaction: Transaction, chainId: BigInt) -> Transaction
