/**
 * Copyright (c) 2015, Lucee Assosication Switzerland. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package lucee.transformer.bytecode;

import java.math.BigDecimal;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import lucee.runtime.config.Config;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.CasterException;
import lucee.runtime.type.util.KeyConstants;
import lucee.transformer.Context;
import lucee.transformer.Factory;
import lucee.transformer.FactoryBase;
import lucee.transformer.Position;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.cast.CastBoolean;
import lucee.transformer.bytecode.cast.CastInt;
import lucee.transformer.bytecode.cast.CastNumber;
import lucee.transformer.bytecode.cast.CastOther;
import lucee.transformer.bytecode.cast.CastString;
import lucee.transformer.bytecode.expression.var.DataMemberImpl;
import lucee.transformer.bytecode.expression.var.EmptyArray;
import lucee.transformer.bytecode.expression.var.EmptyStruct;
import lucee.transformer.bytecode.expression.var.VariableImpl;
import lucee.transformer.bytecode.literal.Empty;
import lucee.transformer.bytecode.literal.LitBooleanImpl;
import lucee.transformer.bytecode.literal.LitIntegerImpl;
import lucee.transformer.bytecode.literal.LitLongImpl;
import lucee.transformer.bytecode.literal.LitNumberImpl;
import lucee.transformer.bytecode.literal.LitStringImpl;
import lucee.transformer.bytecode.literal.Null;
import lucee.transformer.bytecode.literal.NullConstant;
import lucee.transformer.bytecode.op.OpBool;
import lucee.transformer.bytecode.op.OpContional;
import lucee.transformer.bytecode.op.OpDecision;
import lucee.transformer.bytecode.op.OpElvis;
import lucee.transformer.bytecode.op.OpNegate;
import lucee.transformer.bytecode.op.OpNegateNumber;
import lucee.transformer.bytecode.op.OpNumber;
import lucee.transformer.bytecode.op.OpString;
import lucee.transformer.bytecode.op.OpUnaryNumber;
import lucee.transformer.bytecode.op.OpUnaryString;
import lucee.transformer.bytecode.util.Types;
import lucee.transformer.expression.ExprBoolean;
import lucee.transformer.expression.ExprInt;
import lucee.transformer.expression.ExprNumber;
import lucee.transformer.expression.ExprString;
import lucee.transformer.expression.Expression;
import lucee.transformer.expression.literal.LitBoolean;
import lucee.transformer.expression.literal.LitInteger;
import lucee.transformer.expression.literal.LitLong;
import lucee.transformer.expression.literal.LitNumber;
import lucee.transformer.expression.literal.LitString;
import lucee.transformer.expression.literal.Literal;
import lucee.transformer.expression.var.DataMember;
import lucee.transformer.expression.var.Variable;

public class BytecodeFactory extends FactoryBase {
	private final static Method INIT = new Method("init", Types.COLLECTION_KEY, new Type[] { Types.STRING });

	private static final Type KEY_CONSTANTS = Type.getType(KeyConstants.class);

	private static BytecodeFactory instance;

	public static Factory getInstance(Config config) {
		if (instance == null) instance = new BytecodeFactory(config == null ? ThreadLocalPageContext.getConfig() : config);
		return instance;
	}

	private final LitBoolean TRUE;
	private final LitBoolean FALSE;
	private final LitString EMPTY;
	private final Expression NULL;
	private final LitNumber NUMBER_ZERO;
	private final LitNumber NUMBER_ONE;

	private final Config config;

	public BytecodeFactory(Config config) {
		TRUE = createLitBoolean(true);
		FALSE = createLitBoolean(false);
		EMPTY = createLitString("");
		NULL = Null.getSingleInstance(this);
		NUMBER_ZERO = createLitNumber(0);
		NUMBER_ONE = createLitNumber(1);
		this.config = config;
	}

	@Override
	public LitString createLitString(String str) {
		return new LitStringImpl(this, str, null, null);
	}

	@Override
	public LitString createLitString(String str, Position start, Position end) {
		return new LitStringImpl(this, str, start, end);
	}

	@Override
	public LitBoolean createLitBoolean(boolean b) {
		return new LitBooleanImpl(this, b, null, null);
	}

	@Override
	public LitBoolean createLitBoolean(boolean b, Position start, Position end) {
		return new LitBooleanImpl(this, b, start, end);
	}

	@Override
	public LitNumber createLitNumber(String number) throws CasterException {
		return createLitNumber(number, null, null);
	}

	@Override
	public LitNumber createLitNumber(String number, Position start, Position end) throws CasterException {
		return new LitNumberImpl(this, number, start, end);
	}

	@Override
	public LitNumber createLitNumber(BigDecimal bd) {
		return createLitNumber(bd, null, null);
	}

	@Override
	public LitNumber createLitNumber(BigDecimal bd, Position start, Position end) {
		return new LitNumberImpl(this, bd, start, end);
	}

	@Override
	public LitNumber createLitNumber(Number n) {
		return createLitNumber(n, null, null);
	}

	@Override
	public LitNumber createLitNumber(Number n, Position start, Position end) {
		return new LitNumberImpl(this, n instanceof BigDecimal ? (BigDecimal) n : BigDecimal.valueOf(n.doubleValue()), start, end);
	}

	@Override
	public LitLong createLitLong(long l) {
		return new LitLongImpl(this, l, null, null);
	}

	@Override
	public LitLong createLitLong(long l, Position start, Position end) {
		return new LitLongImpl(this, l, start, end);
	}

	@Override
	public LitInteger createLitInteger(int i) {
		return new LitIntegerImpl(this, i, null, null);
	}

	@Override
	public LitInteger createLitInteger(int i, Position start, Position end) {
		return new LitIntegerImpl(this, i, start, end);
	}

	@Override
	public boolean isNull(Expression e) {
		return e instanceof Null;
	}

	@Override
	public Expression createNull() {
		return new Null(this, null, null);
	}

	@Override
	public Expression createNull(Position start, Position end) {
		return new Null(this, start, end);
	}

	@Override
	public Expression createNullConstant(Position start, Position end) {
		return new NullConstant(this, null, null);
	}

	@Override
	public Expression createEmpty() {
		return new Empty(this, null, null);
	}

	@Override
	public DataMember createDataMember(ExprString name) {
		return new DataMemberImpl(name);
	}

	@Override
	public LitBoolean TRUE() {
		return TRUE;
	}

	@Override
	public LitBoolean FALSE() {
		return FALSE;
	}

	@Override
	public LitString EMPTY() {
		return EMPTY;
	}

	@Override
	public LitNumber NUMBER_ZERO() {
		return NUMBER_ZERO;
	}

	@Override
	public LitNumber NUMBER_ONE() {
		return NUMBER_ONE;
	}

	@Override
	public Expression NULL() {
		return NULL;
	}

	@Override
	public ExprNumber toExprNumber(Expression expr) {
		return CastNumber.toExprNumber(expr);
	}

	@Override
	public ExprString toExprString(Expression expr) {
		return CastString.toExprString(expr);
	}

	@Override
	public ExprBoolean toExprBoolean(Expression expr) {
		return CastBoolean.toExprBoolean(expr);
	}

	@Override
	public ExprInt toExprInt(Expression expr) {
		return CastInt.toExprInt(expr);
	}

	@Override
	public Expression toExpression(Expression expr, String type) {
		return CastOther.toExpression(expr, type);
	}

	@Override
	public Variable createVariable(Position start, Position end) {
		return new VariableImpl(this, start, end);
	}

	@Override
	public Variable createVariable(int scope, Position start, Position end) {
		return new VariableImpl(this, scope, start, end);
	}

	@Override
	public ExprString opString(Expression left, Expression right) {
		return OpString.toExprString(left, right, true);
	}

	@Override
	public ExprString opString(Expression left, Expression right, boolean concatStatic) {
		return OpString.toExprString(left, right, concatStatic);
	}

	@Override
	public ExprBoolean opBool(Expression left, Expression right, int operation) {
		return OpBool.toExprBoolean(left, right, operation);
	}

	@Override
	public ExprNumber opNumber(Expression left, Expression right, int operation) {
		return OpNumber.toExprNumber(left, right, operation);
	}

	@Override
	public Expression opNegate(Expression expr, Position start, Position end) {
		return OpNegate.toExprBoolean(expr, start, end);
	}

	@Override
	public Expression removeCastString(Expression expr) {
		while (true) {
			if (expr instanceof CastString) {
				expr = ((CastString) expr).getExpr();

			}
			else if (expr instanceof CastOther && (((CastOther) expr).getType().equalsIgnoreCase("String") || ((CastOther) expr).getType().equalsIgnoreCase("java.lang.String"))) {
				expr = ((CastOther) expr).getExpr();
			}
			else break;
		}
		return expr;
	}

	@Override
	public void registerKey(Context c, Expression name, boolean doUpperCase) throws TransformerException {
		BytecodeContext bc = (BytecodeContext) c;
		if (name instanceof Literal) {
			Literal l = (Literal) name;

			LitString ls = name instanceof LitString ? (LitString) l : c.getFactory().createLitString(l.getString());
			if (doUpperCase) {
				ls = ls.duplicate();
				ls.upperCase();
			}
			String key = KeyConstants.getFieldName(ls.getString());
			if (key != null) {
				bc.getAdapter().getStatic(KEY_CONSTANTS, key, Types.COLLECTION_KEY);
				return;
			}

			int index = bc.registerKey(ls);
			bc.getAdapter().visitFieldInsn(Opcodes.GETSTATIC, bc.getClassName(), "keys", Types.COLLECTION_KEY_ARRAY.toString());
			bc.getAdapter().push(index);
			bc.getAdapter().visitInsn(Opcodes.AALOAD);

			return;
		}
		name.writeOut(bc, Expression.MODE_REF);
		bc.getAdapter().invokeStatic(Page.KEY_IMPL, Page.KEY_SOURCE);
		// bc.getAdapter().invokeStatic(Types.CASTER, TO_KEY);
		return;
	}

	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public Expression createStruct() {
		return new EmptyStruct(this);
	}

	@Override
	public Expression createArray() {
		return new EmptyArray(this);
	}

	@Override
	public ExprNumber opUnaryNumber(Variable var, Expression value, short type, int operation, Position start, Position end) {
		return new OpUnaryNumber(var, value, type, operation, start, end);
	}

	@Override
	public ExprString opUnaryString(Variable var, Expression value, short type, int operation, Position start, Position end) {
		return new OpUnaryString(var, value, type, operation, start, end);
	}

	@Override
	public Expression opContional(Expression cont, Expression left, Expression right) {
		return OpContional.toExpr(cont, left, right);
	}

	@Override
	public ExprBoolean opDecision(Expression left, Expression right, int operation) {
		return OpDecision.toExprBoolean(left, right, operation);
	}

	@Override
	public Expression opElvis(Variable left, Expression right) {
		return OpElvis.toExpr(left, right);
	}

	@Override
	public ExprNumber opNegateNumber(Expression expr, int operation, Position start, Position end) {
		return OpNegateNumber.toExprNumber(expr, operation, start, end);
	}
}