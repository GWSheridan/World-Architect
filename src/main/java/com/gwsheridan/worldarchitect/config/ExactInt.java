package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.PrimitiveCodec;

/** Strict readers for new integer fields; writers retain native integer tags. */
public final class ExactInt {
    private ExactInt(){}
    public static Codec<Integer> range(int min,int max){return new PrimitiveCodec<Integer>(){
        @Override public <T> DataResult<Integer> read(DynamicOps<T> ops,T input){return ops.getNumberValue(input).flatMap(number->{
            try{return DataResult.success(new java.math.BigDecimal(number.toString()).intValueExact());}
            catch(ArithmeticException|NumberFormatException e){return DataResult.error(()->"Expected an exact 32-bit integer.");}
        });}
        @Override public <T> T write(DynamicOps<T> ops,Integer value){return ops.createInt(value);}
    }.validate(value->value>=min&&value<=max?DataResult.success(value):DataResult.error(()->"Integer must be between "+min+" and "+max+"."));}
}
