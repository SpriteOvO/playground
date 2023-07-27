// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.scalafmt._
import scalalib._
// Hack
import publish._
// support BSP
import mill.bsp._
// input build.sc from each repositories.
import $file.dependencies.chisel.build
import $file.dependencies.cde.build
import $file.dependencies.`berkeley-hardfloat`.build
import $file.dependencies.`rocket-chip`.common
import $file.dependencies.binder.common

// Global Scala Version
object ivys {
  val sv = "2.13.10"
  val upickle = ivy"com.lihaoyi::upickle:1.3.15"
  val oslib = ivy"com.lihaoyi::os-lib:0.7.8"
  val pprint = ivy"com.lihaoyi::pprint:0.6.6"
  val utest = ivy"com.lihaoyi::utest:0.7.10"
  val jline = ivy"org.scala-lang.modules:scala-jline:2.12.1"
  val scalatest = ivy"org.scalatest::scalatest:3.2.2"
  val scalatestplus = ivy"org.scalatestplus::scalacheck-1-14:3.1.1.1"
  val scalacheck = ivy"org.scalacheck::scalacheck:1.14.3"
  val scopt = ivy"com.github.scopt::scopt:3.7.1"
  val playjson = ivy"com.typesafe.play::play-json:2.9.4"
  val breeze = ivy"org.scalanlp::breeze:1.1"
  val parallel = ivy"org.scala-lang.modules:scala-parallel-collections_3:1.0.4"
  val mainargs = ivy"com.lihaoyi::mainargs:0.4.0"
}

// For modules not support mill yet, need to have a ScalaModule depend on our own repositories.
trait CommonModule extends ScalaModule {
  override def scalaVersion = ivys.sv

  override def scalacPluginClasspath = T {
    super.scalacPluginClasspath() ++ Agg(
      mychisel.pluginModule.jar()
    )
  }

  override def scalacOptions = T {
    super
      .scalacOptions() ++ Agg(s"-Xplugin:${mychisel.pluginModule.jar().path}", "-Ymacro-annotations", "-Ytasty-reader")
  }

  override def moduleDeps: Seq[ScalaModule] = Seq(mychisel)
}

object mychisel extends dependencies.chisel.build.Chisel(ivys.sv) {
  override def millSourcePath = os.pwd / "dependencies" / "chisel"
}

object mycde extends dependencies.cde.build.cde(ivys.sv) with PublishModule {
  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
}

object myrocketchip extends dependencies.`rocket-chip`.common.CommonRocketChip {

  override def scalacPluginClasspath = T {
    super.scalacPluginClasspath() ++ Agg(
      mychisel.pluginModule.jar()
    )
  }

  override def scalacOptions = T {
    super.scalacOptions() ++ Agg(s"-Xplugin:${mychisel.pluginModule.jar().path}", "-Ymacro-annotations")
  }

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip"

  override def scalaVersion = ivys.sv

  def chisel3Module: Option[PublishModule] = Some(mychisel)

  def hardfloatModule: PublishModule = myhardfloat

  def cdeModule: PublishModule = mycde
}

object inclusivecache extends CommonModule {

  override def millSourcePath =
    os.pwd / "dependencies" / "rocket-chip-inclusive-cache" / 'design / 'craft / "inclusivecache"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object blocks extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-blocks"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object shells extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-fpga-shells"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
}

// UCB
object myhardfloat extends dependencies.`berkeley-hardfloat`.build.hardfloat {
  override def millSourcePath = os.pwd / "dependencies" / "berkeley-hardfloat"

  override def scalaVersion = ivys.sv

  def chisel3Module: Option[PublishModule] = Some(mychisel)

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivys.parallel
  )

  override def scalacOptions = T {
    Seq(s"-Xplugin:${mychisel.pluginModule.jar().path}")
  }
  override def scalacPluginClasspath = T {
    super.scalacPluginClasspath() ++ Agg(
      mychisel.pluginModule.jar()
    )
  }
}

object circt extends Module {
  def circtSourcePath = os.pwd / "dependencies" / "circt"

  def llvmSourcePath = os.pwd / "dependencies" / "llvm-project"

  def installDirectory = T {
    T.dest
  }

  def install = T {
    os.proc("ninja", "-j64", "install").call(cmake())
  }

  def cmake = T.persistent {
    // @formatter:off
    os.proc(
      "cmake",
      "-S", llvmSourcePath / "llvm",
      "-B", T.dest,
      "-G", "Ninja",
      s"-DCMAKE_INSTALL_PREFIX=${installDirectory()}",
      "-DCMAKE_BUILD_TYPE=RelWithDebInfo",
      "-DLLVM_ENABLE_PROJECTS=mlir",
      "-DLLVM_TARGETS_TO_BUILD=X86",
      "-DLLVM_ENABLE_ASSERTIONS=ON",
      "-DLLVM_BUILD_EXAMPLES=OFF",
      "-DLLVM_INCLUDE_EXAMPLES=OFF",
      "-DLLVM_INCLUDE_TESTS=OFF",
      "-DLLVM_INSTALL_UTILS=OFF",
      "-DLLVM_ENABLE_OCAMLDOC=OFF",
      "-DLLVM_ENABLE_BINDINGS=OFF",
      "-DLLVM_CCACHE_BUILD=OFF",
      "-DLLVM_BUILD_TOOLS=OFF",
      "-DLLVM_OPTIMIZED_TABLEGEN=ON",
      "-DLLVM_USE_SPLIT_DWARF=ON",
      "-DLLVM_BUILD_LLVM_DYLIB=OFF",
      "-DLLVM_LINK_LLVM_DYLIB=OFF",
      "-DLLVM_EXTERNAL_PROJECTS=circt",
      "-DBUILD_SHARED_LIBS=ON",
      s"-DLLVM_EXTERNAL_CIRCT_SOURCE_DIR=$circtSourcePath"
    ).call(T.dest)
    // @formatter:on
    T.dest
  }
}

object `circt-jextract` extends common.ChiselCIRCTBinderPublishModule with JavaModule {
  def javacVersion = T.input {
    val version = os
      .proc("javac", "-version")
      .call()
      .out
      .text
      .split(' ')
      .last
      .split('.')
      .head
      .toInt
    require(version >= 20, "Java 20 or higher is required")
    version
  }

  override def javacOptions: T[Seq[String]] = {
    Seq("--enable-preview", "--source", javacVersion().toString)
  }

  def jextractTarGz = T.persistent {
    val f = T.dest / "jextract.tar.gz"
    if (!os.exists(f))
      Util.download(
        s"https://download.java.net/java/early_access/jextract/1/openjdk-20-jextract+1-2_linux-x64_bin.tar.gz",
        os.rel / "jextract.tar.gz"
      )
    PathRef(f)
  }

  def jextract = T.persistent {
    os.proc("tar", "xvf", jextractTarGz().path).call(T.dest)
    PathRef(T.dest / "jextract-20" / "bin" / "jextract")
  }

  // Generate all possible bindings
  def dumpAllIncludes = T {
    val f = os.temp()
    // @formatter:off
    os.proc(
      jextract().path,
      circt.installDirectory() / "include" / "circt-c" / "Dialect" / "FIRRTL.h",
      "-I", circt.installDirectory() / "include",
      "-I", circt.llvmSourcePath / "mlir" / "include",
      "--dump-includes", f
    ).call()
    // @formatter:on
    os.read.lines(f).filter(s => s.nonEmpty && !s.startsWith("#"))
  }

  def includeFunctions = T {
    Seq(
      //
      // MLIR
      "mlirContextCreate",
      "mlirContextDestroy",
      "mlirGetDialectHandle__firrtl__",
      "mlirGetDialectHandle__chirrtl__",
      "mlirDialectHandleLoadDialect",
      // "mlirStringRefCreate", // inline function cannot be generated
      "mlirStringRefCreateFromCString",
      "mlirLocationGetAttribute",
      "mlirLocationUnknownGet",
      "mlirLocationFileLineColGet",
      "mlirModuleCreateEmpty",
      "mlirModuleDestroy",
      "mlirModuleGetBody",
      "mlirModuleGetOperation",
      "mlirOperationStateGet",
      "mlirNamedAttributeGet",
      "mlirIntegerAttrGet",
      "mlirFloatAttrDoubleGet",
      "mlirStringAttrGet",
      "mlirArrayAttrGet",
      "mlirTypeAttrGet",
      "mlirArrayAttrGet",
      "mlirUnitAttrGet",
      ////////////////////
      // Integer types
      ////////////////////
      "mlirIntegerTypeGet",
      "mlirIntegerTypeUnsignedGet",
      "mlirIntegerTypeSignedGet",
      ////////////////////
      "mlirF64TypeGet",
      "mlirNoneTypeGet",
      ////////////////////
      "mlirIdentifierGet",
      "mlirFlatSymbolRefAttrGet",
      // "mlirAttributeParseGet", // We should not "parse" anything
      "mlirOperationStateAddOperands",
      "mlirOperationStateAddResults",
      "mlirOperationStateAddAttributes",
      "mlirOperationGetResult",
      "mlirRegionCreate",
      "mlirOperationCreate",
      "mlirBlockCreate",
      "mlirBlockGetArgument",
      "mlirBlockAppendOwnedOperation",
      "mlirRegionAppendOwnedBlock",
      "mlirOperationStateAddOwnedRegions",
      "mlirOperationDump",
      "mlirExportFIRRTL",
      //
      // FIRRTL Type
      "firrtlTypeGetUInt",
      "firrtlTypeGetSInt",
      "firrtlTypeGetClock",
      "firrtlTypeGetReset",
      "firrtlTypeGetAsyncReset",
      "firrtlTypeGetAnalog",
      "firrtlTypeGetVector",
      "firrtlTypeGetBundle",
      //
      // FIRRTL Attribute
      "firrtlAttrGetPortDirs",
      "firrtlAttrGetParamDecl",
      "firrtlAttrGetNameKind",
      "firrtlAttrGetRUW",
      "firrtlAttrGetMemoryInit",
      "firrtlAttrGetMemDir",
      //
      // CHIRRTL Attribute
      "chirrtlTypeGetCMemory",
      "chirrtlTypeGetCMemoryPort"
    )
  }

  def includeConstants = T {
    Seq(
      // enum FIRRTLPortDirection
      "FIRRTL_PORT_DIR_INPUT",
      "FIRRTL_PORT_DIR_OUTPUT",
      // enum FIRRTLNameKind
      "FIRRTL_NAME_KIND_DROPPABLE_NAME",
      "FIRRTL_NAME_KIND_INTERESTING_NAME",
      // enum FIRRTLRUW
      "FIRRTL_RUW_UNDEFINED",
      "FIRRTL_RUW_OLD",
      "FIRRTL_RUW_NEW",
      // enum FIRRTLMemDir
      "FIRRTL_MEM_DIR_INFER",
      "FIRRTL_MEM_DIR_READ",
      "FIRRTL_MEM_DIR_WRITE",
      "FIRRTL_MEM_DIR_READ_WRITE"
    )
  }

  def includeStructs = T {
    Seq(
      "MlirContext",
      "MlirDialectHandle",
      "MlirStringRef",
      "MlirType",
      "MlirValue",
      "MlirLocation",
      "MlirAttribute",
      "MlirIdentifier",
      "MlirModule",
      "MlirBlock",
      "MlirRegion",
      "MlirOperation",
      "MlirOperationState",
      "MlirNamedAttribute",
      "FIRRTLBundleField"
    )
  }

  def includeTypedefs = T {
    Seq(
      "MlirStringCallback"
    )
  }

  def includeUnions = T {
    Seq.empty[String]
  }

  def includeVars = T {
    Seq.empty[String]
  }

  override def generatedSources: T[Seq[PathRef]] = T {
    circt.install()
    // @formatter:off
    os.proc(
      Seq(
        jextract().path.toString,
        (os.pwd / "dependencies" / "binder" / "chisel-circt-binder" / "jextract-headers.h").toString,
        "-I", (circt.installDirectory() / "include").toString,
        "-I", (circt.llvmSourcePath / "mlir" / "include").toString,
        "-t", "org.llvm.circt",
        "-l", "MLIRCAPIIR",
        "-l", "CIRCTCAPIFIRRTL",
        "-l", "CIRCTCAPICHIRRTL",
        "-l", "CIRCTCAPIHW",
        "-l", "CIRCTCAPIExportFIRRTL",
        "-l", "CIRCTCAPIExportVerilog",
        "-l", "CIRCTFIRRTL",
        "-l", "CIRCTHW",
        "-l", "CIRCTExportFIRRTL",
        "-l", "CIRCTExportVerilog",
        "-l", "MLIRCAPIRegisterEverything",
        "--header-class-name", "CAPI",
        "--source",
        "--output", T.dest.toString
      ) ++ includeFunctions().flatMap(f => Seq("--include-function", f)) ++
        includeConstants().flatMap(f => Seq("--include-constant", f)) ++
        includeStructs().flatMap(f => Seq("--include-struct", f)) ++
        includeTypedefs().flatMap(f => Seq("--include-typedef", f)) ++
        includeUnions().flatMap(f => Seq("--include-union", f)) ++
        includeVars().flatMap(f => Seq("--include-var", f))
    ).call()
    // @formatter:on
    Lib
      .findSourceFiles(os.walk(T.dest).map(PathRef(_)), Seq("java"))
      .distinct
      .map(PathRef(_))
  }

  // mill doesn't happy with the --enable-preview flag, so we work around it
  final override def compile: T[mill.scalalib.api.CompilationResult] = T {
    os.proc(
      Seq("javac", "-d", T.dest.toString) ++ javacOptions() ++ allSourceFiles()
        .map(_.path.toString)
    ).call(T.dest)
    mill.scalalib.api.CompilationResult(os.root, PathRef(T.dest))
  }
}

object `chisel-circt-binder` extends common.ChiselCIRCTBinderModule with ScalaModule with ScalafmtModule {
  override def millSourcePath = os.pwd / "dependencies" / "binder" / "chisel-circt-binder"
  override def scalaVersion = ivys.sv

  def circtJextractModule = `circt-jextract`

  def chisel3Module = Some(mychisel)

  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:3.6.0"
  )
}

// Dummy

object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks, shells, `chisel-circt-binder`)

  override def forkArgs: T[Seq[String]] = {
    Seq(
      "--enable-native-access=ALL-UNNAMED",
      "--enable-preview",
      s"-Djava.library.path=${circt.installDirectory() / "lib"}"
    )
  }

  // add some scala ivy module you like here.
  override def ivyDeps = Agg(
    ivys.oslib,
    ivys.pprint,
    ivys.mainargs
  )

  def lazymodule: String = "freechips.rocketchip.system.ExampleRocketSystem"

  def configs: String = "playground.PlaygroundConfig"

  def elaborate = T {
    mill.modules.Jvm.runSubprocess(
      finalMainClass(),
      runClasspath().map(_.path),
      forkArgs(),
      forkEnv(),
      Seq(
        "--dir",
        T.dest.toString,
        "--lm",
        lazymodule,
        "--configs",
        configs
      ),
      workingDir = os.pwd
    )
    PathRef(T.dest)
  }

  def verilog = T {
    os.proc(
      "firtool",
      elaborate().path / s"${lazymodule.split('.').last}.fir",
      "-disable-infer-rw",
      "--disable-annotation-unknown",
      "-dedup",
      "-O=debug",
      "--split-verilog",
      "--preserve-values=named",
      "--output-annotation-file=mfc.anno.json",
      s"-o=${T.dest}"
    ).call(T.dest)
    PathRef(T.dest)
  }

}
