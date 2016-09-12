package com.act.lcms

import scala.io.Source

class UntargetedPeak(val mz: Double, val rt: Double, val integratedInt: Double, val maxInt: Double, val snr: Double)

class UntargetedPeakSpectra(val peaks: Set[UntargetedPeak]) {

  // read from file
  sealed class XCMSCol(val id: String)
  object MZ extends XCMSCol("mz")
  object RT extends XCMSCol("rt")
  object IntIntensity extends XCMSCol("into")
  object MaxIntensity extends XCMSCol("maxo")
  object SNR extends XCMSCol("sn")
  val hdrsXCMS = List(MZ, RT, IntIntensity, MaxIntensity, SNR)

  def fromXCMSCentwave(file: String): UntargetedPeakSpectra = {
    // example to process (with header):
    // mz  mzmin mzmax rt  rtmin rtmax into  intb  maxo  sn  sample
    // 244.982727097748  244.979644775391  244.985244750977  2.56099998950958  1.91800005733967  2.98900008201599  130.32171994603 129.464919926289  253.177856445312  252 1
    // Such are the files in /mnt/shared-data/Vijay/perlstein_xcms_centwave_optimized_output
    val lines = Source.fromFile(file).getLines.toList.map(_.split("\t").toList)
    val hdr::tail = lines
    val identifiedHdrs = hdr.map(hid => hdrsXCMS.find(_.id.equals(hid)))
    val withHdrs = tail.map(l => hdr.zip(l))
    val relevantLines = withHdrs.map(line => line.filter(kv => kv._1 != None))

    val peaks = Set[UntargetedPeak]()

    new UntargetedPeakSpectra(peaks)
  }
}
