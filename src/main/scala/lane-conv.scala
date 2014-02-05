package hwacha

import Chisel._
import Node._
import Constants._
import Compaction._

class LaneConv(implicit conf: HwachaConfiguration) extends Module 
{
  val io = new Bundle
  {
    val valid = Bool(INPUT)
    val fn = Bits(INPUT, SZ_VAU2_FN)
    val in = Bits(INPUT, SZ_DATA)
    val exc = Bits(OUTPUT, SZ_EXC)
    val out = Bits(OUTPUT, SZ_DATA)
  }

  def VAU2_FN(ins: Bits*) = ins.toList.map(x => {io.fn(RG_VAU2_FN) === x}).reduceLeft( _ || _ )
  def VAU2_FP(fp: Bits) = io.fn(RG_VAU2_FP) === fp

  val op_int2float = MuxCase(
    Bits(0,2), Array(
      VAU2_FN(vau2_CLTF)  -> hardfloat.fpu_recoded.type_int64,
      VAU2_FN(vau2_CLUTF) -> hardfloat.fpu_recoded.type_uint64,
      VAU2_FN(vau2_CWTF)  -> hardfloat.fpu_recoded.type_int32,
      VAU2_FN(vau2_CWUTF) -> hardfloat.fpu_recoded.type_uint32
    ))

  val op_float2int = MuxCase(
    Bits(0,2), Array(
      VAU2_FN(vau2_CFTL)  -> hardfloat.fpu_recoded.type_int64,
      VAU2_FN(vau2_CFTLU) -> hardfloat.fpu_recoded.type_uint64,
      VAU2_FN(vau2_CFTW)  -> hardfloat.fpu_recoded.type_int32,
      VAU2_FN(vau2_CFTWU) -> hardfloat.fpu_recoded.type_uint32
    ))

  val val_int2float_sp = io.valid & VAU2_FP(FPS) & VAU2_FN(vau2_CLTF,vau2_CLUTF,vau2_CWTF,vau2_CWUTF)
  val val_float2int_sp = io.valid & VAU2_FP(FPS) & VAU2_FN(vau2_CFTL,vau2_CFTLU,vau2_CFTW,vau2_CFTWU)
  val val_decode_sp    = io.valid & VAU2_FP(FPS) & VAU2_FN(vau2_MFTX)
  val val_encode_sp    = io.valid & VAU2_FP(FPS) & VAU2_FN(vau2_MXTF)
  val val_dp2sp        = io.valid & VAU2_FN(vau2_CDTS)
  val val_int2float_dp = io.valid & VAU2_FP(FPD) & VAU2_FN(vau2_CLTF,vau2_CLUTF,vau2_CWTF,vau2_CWUTF)
  val val_float2int_dp = io.valid & VAU2_FP(FPD) & VAU2_FN(vau2_CFTL,vau2_CFTLU,vau2_CFTW,vau2_CFTWU)
  val val_decode_dp    = io.valid & VAU2_FP(FPD) & VAU2_FN(vau2_MFTX)
  val val_encode_dp    = io.valid & VAU2_FP(FPD) & VAU2_FN(vau2_MXTF)
  val val_sp2dp        = io.valid & VAU2_FN(vau2_CSTD)

  val int2float_sp = Module(new hardfloat.anyToRecodedFloat32)
  int2float_sp.io.in := Fill(64,val_int2float_sp) & io.in(63,0)
  int2float_sp.io.roundingMode := Fill(3,val_int2float_sp) & io.fn(RG_VAU2_RM)
  int2float_sp.io.typeOp := Fill(2,val_int2float_sp) & op_int2float
  val result_int2float_sp = pack_float_s(int2float_sp.io.out, 0)
  val exc_int2float_sp = int2float_sp.io.exceptionFlags

  val float2int_sp = Module(new hardfloat.recodedFloat32ToAny)
  float2int_sp.io.in := Fill(33,val_float2int_sp) & unpack_float_s(io.in, 0)
  float2int_sp.io.roundingMode := Fill(3,val_float2int_sp) & io.fn(RG_VAU2_RM)
  float2int_sp.io.typeOp := Fill(2,val_float2int_sp) & op_float2int
  val result_float2int_sp = float2int_sp.io.out
  val exc_float2int_sp = float2int_sp.io.exceptionFlags

  val decode_sp = Module(new hardfloat.recodedFloat32ToFloat32)
  decode_sp.io.in := Fill(33,val_decode_sp) & unpack_float_s(io.in, 0)
  val result_decode_sp = decode_sp.io.out

  val encode_sp = Module(new hardfloat.float32ToRecodedFloat32)
  encode_sp.io.in := Fill(32,val_encode_sp) & io.in(31,0)
  val result_encode_sp = pack_float_s(encode_sp.io.out, 0)

  val dp2sp = Module(new hardfloat.recodedFloat64ToRecodedFloat32)
  dp2sp.io.in := Fill(65,val_dp2sp) & unpack_float_d(io.in, 0)
  dp2sp.io.roundingMode := Fill(3,val_dp2sp) & io.fn(RG_VAU2_RM)
  val result_float2float_sp = pack_float_s(dp2sp.io.out, 0)
  val exc_float2float_sp = dp2sp.io.exceptionFlags

  val int2float_dp = Module(new hardfloat.anyToRecodedFloat64) 
  int2float_dp.io.in := Fill(64,val_int2float_dp) & io.in(63,0)
  int2float_dp.io.roundingMode := Fill(3,val_int2float_dp) & io.fn(RG_VAU2_RM)
  int2float_dp.io.typeOp := Fill(2,val_int2float_dp) & op_int2float
  val result_int2float_dp = pack_float_d(int2float_dp.io.out, 0)
  val exc_int2float_dp = int2float_dp.io.exceptionFlags

  val float2int_dp = Module(new hardfloat.recodedFloat64ToAny)
  float2int_dp.io.in := Fill(65,val_float2int_dp) & unpack_float_d(io.in, 0)
  float2int_dp.io.roundingMode := Fill(3,val_float2int_dp) & io.fn(RG_VAU2_RM)
  float2int_dp.io.typeOp := Fill(2,val_float2int_dp) & op_float2int
  val result_float2int_dp = float2int_dp.io.out
  val exc_float2int_dp = float2int_dp.io.exceptionFlags

  val decode_dp = Module(new hardfloat.recodedFloat64ToFloat64)
  decode_dp.io.in := Fill(65,val_decode_dp) & unpack_float_d(io.in, 0)
  val result_decode_dp = decode_dp.io.out

  val encode_dp = Module(new hardfloat.float64ToRecodedFloat64)
  encode_dp.io.in := Fill(64,val_encode_dp) & io.in(63,0)
  val result_encode_dp = pack_float_d(encode_dp.io.out, 0)

  val sp2dp = Module(new hardfloat.rF32_rF64)
  sp2dp.io.in := Fill(33,val_sp2dp) & unpack_float_s(io.in, 0)
  val result_float2float_dp = pack_float_d(sp2dp.io.out, 0)
  val exc_float2float_dp = sp2dp.io.exception_flags

  val next_result_sp = MuxCase(
    Bits(0, SZ_DATA), Array(
      VAU2_FN(vau2_CLTF,vau2_CLUTF,vau2_CWTF,vau2_CWUTF) -> result_int2float_sp,
      VAU2_FN(vau2_MXTF) -> result_encode_sp,
      VAU2_FN(vau2_CFTL,vau2_CFTLU) -> Cat(Bits(0,1),result_float2int_sp(63,0)),
      VAU2_FN(vau2_CFTW,vau2_CFTWU) -> Cat(Bits(0,1),Fill(32,result_float2int_sp(31)),result_float2int_sp(31,0)),
      VAU2_FN(vau2_MFTX) -> Cat(Bits(0,1),Fill(32,result_decode_sp(31)),result_decode_sp(31,0)),
      VAU2_FN(vau2_CDTS) -> result_float2float_sp
    ))

  val next_result_dp = MuxCase(
    Bits(0, SZ_DATA), Array(
      VAU2_FN(vau2_CLTF,vau2_CLUTF,vau2_CWTF,vau2_CWUTF) -> result_int2float_dp,
      VAU2_FN(vau2_MXTF) -> result_encode_dp,
      VAU2_FN(vau2_CFTL,vau2_CFTLU) -> Cat(Bits(0,1),result_float2int_dp(63,0)),
      VAU2_FN(vau2_CFTW,vau2_CFTWU) -> Cat(Bits(0,1),Fill(32,result_float2int_dp(31)),result_float2int_dp(31,0)),
      VAU2_FN(vau2_MFTX) -> Cat(Bits(0,1),result_decode_dp(63,0)),
      VAU2_FN(vau2_CSTD) -> result_float2float_dp
    ))

  val next_exc_sp = MuxCase(
    Bits(0, SZ_EXC), Array(
      VAU2_FN(vau2_CLTF,vau2_CLUTF,vau2_CWTF,vau2_CWUTF) -> exc_int2float_sp,
      VAU2_FN(vau2_CFTL,vau2_CFTLU,vau2_CFTW,vau2_CFTWU) -> exc_float2int_sp,
      VAU2_FN(vau2_CDTS) -> exc_float2float_sp
    ))

  val next_exc_dp = MuxCase(
    Bits(0, SZ_EXC), Array(
      VAU2_FN(vau2_CLTF,vau2_CLUTF,vau2_CWTF,vau2_CWUTF) -> exc_int2float_dp,
      VAU2_FN(vau2_CFTL,vau2_CFTLU,vau2_CFTW,vau2_CFTWU) -> exc_float2int_dp,
      VAU2_FN(vau2_CSTD) -> exc_float2float_dp
    ))

  val result = Mux(
    VAU2_FP(FPD), Cat(next_exc_dp, next_result_dp),
    Cat(next_exc_sp, next_result_sp))

  val pipereg = ShiftRegister(result, conf.fconv_stages, io.valid)

  Match(pipereg, io.exc, io.out)
}
