/*
 * Copyright (c) 2014, 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.dispatch;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.object.DynamicObject;

import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.methods.InternalMethod;

public class CachedUnboxedDispatchNode extends CachedDispatchNode {

    private final Class<?> expectedClass;
    private final Assumption unmodifiedAssumption;

    private final InternalMethod method;
    @Child private DirectCallNode callNode;

    public CachedUnboxedDispatchNode(
            RubyContext context,
            Object cachedName,
            DispatchNode next,
            Class<?> expectedClass,
            Assumption unmodifiedAssumption,
            InternalMethod method,
            DispatchAction dispatchAction) {
        super(context, cachedName, next, dispatchAction);

        this.expectedClass = expectedClass;
        this.unmodifiedAssumption = unmodifiedAssumption;
        this.method = method;
        this.callNode = Truffle.getRuntime().createDirectCallNode(method.getCallTarget());

        if (callNode.isCallTargetCloningAllowed() && method.getSharedMethodInfo().shouldAlwaysClone()) {
            insert(callNode);
            callNode.cloneCallTarget();
        }
    }

    @Override
    protected boolean guard(Object methodName, Object receiver) {
        return guardName(methodName) &&
                !(receiver instanceof DynamicObject) &&
                (receiver.getClass() == expectedClass);
    }

    @Override
    public Object executeDispatch(
            VirtualFrame frame,
            Object receiverObject,
            Object methodName,
            DynamicObject blockObject,
            Object[] argumentsObjects) {
        if (!guard(methodName, receiverObject)) {
            return next.executeDispatch(
                    frame,
                    receiverObject,
                    methodName,
                    blockObject,
                    argumentsObjects);
        }

        // Check the class has not been modified

        try {
            unmodifiedAssumption.check();
        } catch (InvalidAssumptionException e) {
            return resetAndDispatch(
                    frame,
                    receiverObject,
                    methodName,
                    blockObject,
                    argumentsObjects,
                    "class modified");
        }

        switch (getDispatchAction()) {
            case CALL_METHOD:
                return call(callNode, frame, method, receiverObject, blockObject, argumentsObjects);

            case RESPOND_TO_METHOD:
                return true;

            default:
                throw new UnsupportedOperationException();
        }
    }

}
