package io.sikorka.android.node.configuration

import org.ethereum.geth.Enodes
import org.ethereum.geth.Geth
import org.ethereum.geth.NodeConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RopstenConfiguration
@Inject
constructor(private val peerHelper: PeerHelper) : IConfiguration {
  override fun prepare() {
    val peerFile = "${dataDir.absolutePath}/GethDroid/static-nodes.json"
    peerHelper.prepareStaticNodes(peerFile, assetFilename)
  }

  override val nodeConfig: NodeConfig
    get() {
      val nodeConfig = Geth.newNodeConfig()
      nodeConfig.ethereumEnabled = true
      nodeConfig.whisperEnabled = true
      nodeConfig.ethereumGenesis = Geth.testnetGenesis()
      nodeConfig.ethereumNetworkID = 3
      nodeConfig.maxPeers = 30
      nodeConfig.ethereumDatabaseCache = 32
      nodeConfig.bootstrapNodes = bootstrapNodes
      return nodeConfig
    }

  override val bootstrapNodes: Enodes?
    get() {
      val nodes = arrayListOf(
          "enode://20c9ad97c081d63397d7b685a412227a40e23c8bdc6688c6f37e97cfbc22d2b4d1db1510d8f61e6a8866ad7f0e17c02b14182d37ea7c3c8b9c2683aeb6b733a1@52.169.14.227:30303",
          "enode://6ce05930c72abc632c58e2e4324f7c7ea478cec0ed4fa2528982cf34483094e9cbc9216e7aa349691242576d552a2a56aaeae426c5303ded677ce455ba1acd9d@13.84.180.240:30303"
      )

      val enodes = Geth.newEnodes(nodes.size.toLong())

      nodes.forEachIndexed { index, node ->
        enodes.set(index.toLong(), Geth.newEnode(node))
      }

      return enodes
    }

  override val dataDir: File
    get() = prepareDataDir(directoryPath)

  companion object {
    const val directoryPath = ".ropsten"
    const val assetFilename = "ropsten_peers.json"
  }
}