// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.system

import freechips.rocketchip.config.Config
import freechips.rocketchip.subsystem._

class WithJtagDTMSystem extends freechips.rocketchip.subsystem.WithJtagDTM
class WithDebugSBASystem extends freechips.rocketchip.subsystem.WithDebugSBA
class WithDebugAPB extends freechips.rocketchip.subsystem.WithDebugAPB

class BaseConfig extends Config(
  new WithDefaultMemPort ++
  new WithDefaultMMIOPort ++
  new WithDefaultSlavePort ++
  new WithTimebase(BigInt(1000000)) ++ // 1 MHz
  new WithDTS("freechips,rocketchip-unknown", Nil) ++
  new WithNExtTopInterrupts(2) ++
  new BaseSubsystemConfig
)

class DefaultConfig extends Config(new WithNBigCores(1) ++ new WithCoherentBusTopology ++ new BaseConfig)

class DefaultBufferlessConfig extends Config(new WithBufferlessBroadcastHub ++ new DefaultConfig)
class DefaultSmallConfig extends Config(new WithNSmallCores(1) ++ new WithCoherentBusTopology ++ new BaseConfig)
class DefaultRV32Config extends Config(new WithRV32 ++ new DefaultConfig)

class DualBankConfig extends Config(new WithNBanks(2) ++ new DefaultConfig)
class DualCoreConfig extends Config(new WithNBigCores(2) ++ new WithCoherentBusTopology ++ new BaseConfig)
class DualChannelConfig extends Config(new WithNMemoryChannels(2) ++ new DefaultConfig)
class EightChannelConfig extends Config(new WithNMemoryChannels(8) ++ new DefaultConfig)

class DualChannelDualBankConfig extends Config(
  new WithNMemoryChannels(2) ++
  new WithNBanks(4) ++ new DefaultConfig
)

class RoccExampleConfig extends Config(new WithRoccExample ++ new DefaultConfig)

class HeterogeneousTileExampleConfig extends Config(
  new WithNBigCores(n = 1) ++
  new WithNMedCores(n = 1) ++
  new WithNSmallCores(n = 1) ++
  new WithCoherentBusTopology ++
  new BaseConfig
)

class Edge128BitConfig extends Config(
  new WithEdgeDataBits(128) ++ new DefaultConfig
)
class Edge32BitConfig extends Config(
  new WithEdgeDataBits(32) ++ new DefaultConfig
)

class SingleChannelBenchmarkConfig extends Config(new DefaultConfig)
class DualChannelBenchmarkConfig extends Config(new WithNMemoryChannels(2) ++ new SingleChannelBenchmarkConfig)
class QuadChannelBenchmarkConfig extends Config(new WithNMemoryChannels(4) ++ new SingleChannelBenchmarkConfig)
class OctoChannelBenchmarkConfig extends Config(new WithNMemoryChannels(8) ++ new SingleChannelBenchmarkConfig)

class TinyConfig extends Config(
  new WithNoMemPort ++
  new WithNMemoryChannels(0) ++
  new WithNBanks(0) ++
  new With1TinyCore ++
  new WithIncoherentBusTopology ++
  new BaseConfig
)

class MemPortOnlyConfig extends Config(
  new WithNoMMIOPort ++
  new WithNoSlavePort ++
  new DefaultConfig
)

class MMIOPortOnlyConfig extends Config(
  new WithNoSlavePort ++
  new WithNoMemPort ++
  new WithNMemoryChannels(0) ++
  new WithNBanks(0) ++
  new WithIncoherentTiles ++
  new WithScratchpadsOnly ++
  new WithIncoherentBusTopology ++
  new DefaultConfig
)

class BaseFPGAConfig extends Config(new BaseConfig ++ new WithCoherentBusTopology)
class DefaultFPGAConfig extends Config(new WithNSmallCores(1) ++ new BaseFPGAConfig)

import Chisel._
import freechips.rocketchip.config._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
// import freechips.rocketchip.util._

class MySubsystemConfig extends Config ((site, here, up) => {
  // Tile parameters
  case PgLevels => if (site(XLen) == 64) 3 /* Sv39 */ else 2 /* Sv32 */
  case XLen => 64 // Applies to all cores
  case MaxHartIdBits => log2Up(site(TilesLocated(InSubsystem)).map(_.tileParams.hartId).max+1)
  // Interconnect parameters
  case SystemBusKey => SystemBusParams(
    beatBytes = site(XLen)/8,
    blockBytes = site(CacheBlockBytes))
  case ControlBusKey => PeripheryBusParams(
    beatBytes = site(XLen)/8,
    blockBytes = site(CacheBlockBytes),
    errorDevice = Some(BuiltInErrorDeviceParams(
      errorParams = DevNullParams(List(AddressSet(0x3000, 0xfff)), maxAtomic=site(XLen)/8, maxTransfer=4096))))
  case PeripheryBusKey => PeripheryBusParams(
    beatBytes = site(XLen)/8,
    blockBytes = site(CacheBlockBytes),
    dtsFrequency = Some(100000000)) // Default to 100 MHz pbus clock
  case MemoryBusKey => MemoryBusParams(
    beatBytes = site(XLen)/8,
    blockBytes = site(CacheBlockBytes))
  case FrontBusKey => FrontBusParams(
    beatBytes = site(XLen)/8,
    blockBytes = site(CacheBlockBytes))
  // Additional device Parameters
  case BootROMLocated(InSubsystem) => Some(BootROMParams(contentFileName = "./bootrom/bootrom.img"))
  case SubsystemExternalResetVectorKey => false
  case DebugModuleKey => None
  case CLINTKey => Some(CLINTParams())
  case PLICKey => Some(PLICParams())
  case TilesLocated(InSubsystem) => 
    LegacyTileFieldHelper(site(RocketTilesKey), site(RocketCrossingKey), RocketTileAttachParams.apply _)
})

// class MyBaseConfig extends Config(
//   new WithDefaultMemPort ++
//   new WithDefaultMMIOPort ++
//   new WithDefaultSlavePort ++
//   new WithTimebase(BigInt(1000000)) ++ // 1 MHz
//   new WithDTS("freechips,rocketchip-unknown", Nil) ++
//   new WithNExtTopInterrupts(2) ++
//   new BaseSubsystemConfig
// )

class WithNoDebug extends Config((site, here, up) => {
  case DebugModuleKey => None
})

// class MyBaseConfig extends Config(
//   new WithNoMemPort ++
//   new WithNMemoryChannels(0) ++
//   new WithNBanks(0) ++
//   new WithTimebase(BigInt(1000000)) ++ // 1 MHz
//   new With1TinyCore ++
//   new WithNExtTopInterrupts(2) ++
//   new BaseSubsystemConfig ++
//   new WithNoDebug
// )

class MyBaseConfig extends Config(
  new WithoutTLMonitors ++
  // new WithDefaultMemPort ++
  new testchipip.WithBackingScratchpad ++                   // add mbus backing scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++       // remove offchip mem port
  new WithDefaultMMIOPort ++
  new WithDefaultSlavePort ++
  new WithTimebase(BigInt(1000000)) ++ // 1 MHz
  new WithDTS("freechips,rocketchip-unknown", Nil) ++
  // new WithDefaultBtb ++
  new WithNExtTopInterrupts(2) ++
  new WithNoDebug ++
  // new WithL1DCacheWays(2) ++
  // new WithL1DCacheSets(16) ++
  new BaseSubsystemConfig
)

class MyBaseFPGAConfig extends Config(new MyBaseConfig ++ new WithCoherentBusTopology)
class MyFPGAConfig extends Config(new WithNSmallCores(1) ++ new MyBaseFPGAConfig)
// class MyFPGAConfig extends Config(
//   new TinyConfig ++
//   new WithNoDebug ++
//   new WithTimebase(BigInt(1000000)) ++ // 1 MHz
//   new WithDefaultBtb ++
//   new WithNExtTopInterrupts(2) ++
//   new WithL1DCacheWays(2) ++
//   new WithL1DCacheSets(16))
