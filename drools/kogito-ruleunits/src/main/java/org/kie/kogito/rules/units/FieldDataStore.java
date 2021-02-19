/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.rules.units;

import java.util.ArrayList;
import java.util.List;

import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.kogito.factory.KogitoInternalFactHandle;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.ruleunit.InternalStoreCallback;
import org.drools.core.spi.Activation;
import org.drools.core.util.bitmask.BitMask;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.kogito.rules.DataHandle;
import org.kie.kogito.rules.DataProcessor;
import org.kie.kogito.rules.SingletonStore;
import org.kie.kogito.rules.units.impl.DataHandleImpl;

public class FieldDataStore<T> implements SingletonStore<T>,
                                          InternalStoreCallback {

    private DataHandleImpl handle = null;

    private final List<EntryPointDataProcessor> entryPointSubscribers = new ArrayList<>();
    private final List<DataProcessor<T>> subscribers = new ArrayList<>();

    public DataHandle set(T t) {
        if (handle == null && t != null) {
            insert(t);
        } else {
            clear();
            if (t != null) {
                insert(t);
            }
        }

        return handle;
    }

    private void insert(T t) {
        handle = new DataHandleImpl(t);
        entryPointSubscribers.forEach(s -> internalInsert(handle, s));
        subscribers.forEach(s -> internalInsert(handle, s));
    }

    public void update() {
        if (handle == null) {
            return;
        }
        DataHandle dh = handle;
        entryPointSubscribers.forEach(s -> s.update(dh, dh.getObject()));
        subscribers.forEach(s -> s.update(dh, (T) dh.getObject()));
    }

    @Override
    public void clear() {
        if (handle == null) {
            return;
        }
        DataHandle dh = handle;
        handle = null;
        entryPointSubscribers.forEach(s -> s.delete(dh));
        subscribers.forEach(s -> s.delete(dh));
    }

    @Override
    public void subscribe(DataProcessor processor) {
        if (processor instanceof EntryPointDataProcessor) {
            EntryPointDataProcessor subscriber = (EntryPointDataProcessor) processor;
            entryPointSubscribers.add(subscriber);
        } else {
            subscribers.add(processor);
        }
        if (handle != null) {
            internalInsert(handle, processor);
        }
    }

    @Override
    public void update(KogitoInternalFactHandle fh, Object obj, BitMask mask, Class<?> modifiedClass, Activation activation) {
        DataHandle dh = ((KogitoInternalFactHandle)fh).getDataHandle();
        entryPointSubscribers.forEach(s -> s.update(dh, obj, mask, modifiedClass, activation));
        subscribers.forEach(s -> s.update(dh, (T) obj));
    }

    @Override
    public void delete(KogitoInternalFactHandle fh, RuleImpl rule, TerminalNode terminalNode, FactHandle.State fhState) {
        DataHandle dh = ((KogitoInternalFactHandle)fh).getDataHandle();
        if (dh != this.handle) {
            throw new IllegalArgumentException("The given handle is not contained in this DataStore");
        }
        entryPointSubscribers.forEach(s -> s.delete(dh, rule, terminalNode, fhState));
        subscribers.forEach(s -> s.delete(dh));
        handle = null;
    }

    private void internalInsert(DataHandle dh, DataProcessor processor) {
        FactHandle fh = processor.insert(dh, dh == null ? null : dh.getObject());
        if (fh != null) {
            ((KogitoInternalFactHandle) fh).setDataStore(this);
            ((KogitoInternalFactHandle) fh).setDataHandle(dh);
        }
    }
}
