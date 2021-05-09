/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.nn.to.xml.test;

import static org.junit.Assert.assertTrue;

import com.google.common.base.VerifyException;
import com.google.common.collect.Iterables;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.opendaylight.controller.sal.rest.impl.test.providers.AbstractBodyReaderTest;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.opendaylight.yangtools.yang.model.ri.type.BaseTypes;
import org.opendaylight.yangtools.yang.model.ri.type.BitsTypeBuilder;
import org.opendaylight.yangtools.yang.model.ri.type.UnionTypeBuilder;

public class NnToXmlTest extends AbstractBodyReaderTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final NormalizedNodeXmlBodyWriter xmlBodyWriter;
    private static EffectiveModelContext schemaContext;

    public NnToXmlTest() {
        super(schemaContext, null);
        this.xmlBodyWriter = new NormalizedNodeXmlBodyWriter();
    }

    @BeforeClass
    public static void initialization() {
        schemaContext = schemaContextLoader("/nn-to-xml/yang", schemaContext);
    }

    @Test
    public void nnAsYangIdentityrefToXMLTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareIdrefData(null, true);
        nnToXml(normalizedNodeContext, "<lf11 xmlns:x=\"referenced:module\">x:iden</lf11>");
    }

    @Test
    public void nnAsYangIdentityrefWithQNamePrefixToXMLTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareIdrefData("prefix", true);
        nnToXml(normalizedNodeContext, "<lf11 xmlns", "=\"referenced:module\">", ":iden</lf11>");
    }

    @Test
    public void nnAsYangIdentityrefWithPrefixToXMLTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareIdrefData("prefix", false);
        nnToXml(normalizedNodeContext, "<lf11>no qname value</lf11>");
    }

    @Test
    public void nnAsYangLeafrefWithPrefixToXMLTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareLeafrefData();
        nnToXml(normalizedNodeContext, "<lfBoolean>true</lfBoolean>", "<lfLfref>true</lfLfref>");
    }

    /**
     * Negative test when leaf of type leafref references to not-existing leaf.
     * {@code VerifyException} is expected.
     */
    @Test
    public void nnAsYangLeafrefWithPrefixToXMLNegativeTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareLeafrefNegativeData();

        thrown.expect(VerifyException.class);
        nnToXml(normalizedNodeContext, "<not-existing>value</not-existing>",
                "<lfLfrefNegative>value</lfLfrefnegative>");
    }

    @Test
    public void nnAsYangStringToXmlTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.stringType()).deserialize("lfStr value"), "lfStr");
        nnToXml(normalizedNodeContext, "<lfStr>lfStr value</lfStr>");
    }

    @Test
    public void nnAsYangInt8ToXmlTest() throws Exception {
        final String elName = "lfInt8";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.int8Type()).deserialize("14"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">14</" + elName + ">");
    }

    @Test
    public void nnAsYangInt16ToXmlTest() throws Exception {
        final String elName = "lfInt16";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.int16Type()).deserialize("3000"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">3000</" + elName + ">");
    }

    @Test
    public void nnAsYangInt32ToXmlTest() throws Exception {
        final String elName = "lfInt32";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.int32Type()).deserialize("201234"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">201234</" + elName + ">");
    }

    @Test
    public void nnAsYangInt64ToXmlTest() throws Exception {
        final String elName = "lfInt64";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.int64Type()).deserialize("5123456789"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">5123456789</" + elName + ">");
    }

    @Test
    public void nnAsYangUint8ToXmlTest() throws Exception {
        final String elName = "lfUint8";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.uint8Type()).deserialize("200"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">200</" + elName + ">");
    }

    @Test
    public void snAsYangUint16ToXmlTest() throws Exception {
        final String elName = "lfUint16";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.uint16Type()).deserialize("4000"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">4000</" + elName + ">");
    }

    @Test
    public void nnAsYangUint32ToXmlTest() throws Exception {
        final String elName = "lfUint32";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.uint32Type()).deserialize("4123456789"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">4123456789</" + elName + ">");
    }

    @Test
    public void snAsYangUint64ToXmlTest() throws Exception {
        final String elName = "lfUint64";
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.uint64Type()).deserialize("5123456789"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">5123456789</" + elName + ">");
    }

    @Test
    public void nnAsYangBinaryToXmlTest() throws Exception {
        final String elName = "lfBinary";
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.binaryType())
                        .deserialize("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz01234567"),
                elName);
        nnToXml(normalizedNodeContext,
                "<" + elName + ">ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz01234567</" + elName + ">");
    }

    @Test
    public void nnAsYangBitsToXmlTest() throws Exception {
        final BitsTypeDefinition.Bit mockBit1 = Mockito.mock(BitsTypeDefinition.Bit.class);
        Mockito.when(mockBit1.getName()).thenReturn("one");
        Mockito.when(mockBit1.getPosition()).thenReturn(Uint32.ONE);
        final BitsTypeDefinition.Bit mockBit2 = Mockito.mock(BitsTypeDefinition.Bit.class);
        Mockito.when(mockBit2.getName()).thenReturn("two");
        Mockito.when(mockBit2.getPosition()).thenReturn(Uint32.TWO);

        final String elName = "lfBits";
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(TypeDefinitionAwareCodec.from(
            BaseTypes.bitsTypeBuilder(QName.create("foo", "foo")).addBit(mockBit1).addBit(mockBit2).build())
            .deserialize("one two"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">one two</" + elName + ">");
    }

    @Test
    public void nnAsYangEnumerationToXmlTest() throws Exception {
        final EnumTypeDefinition.EnumPair mockEnum = Mockito.mock(EnumTypeDefinition.EnumPair.class);
        Mockito.when(mockEnum.getName()).thenReturn("enum2");

        final String elName = "lfEnumeration";
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(TypeDefinitionAwareCodec.from(
            BaseTypes.enumerationTypeBuilder(QName.create("foo", "foo")).addEnum(mockEnum).build())
            .deserialize("enum2"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">enum2</" + elName + ">");
    }

    @Test
    public void nnAsYangEmptyToXmlTest() throws Exception {
        final String elName = "lfEmpty";
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.emptyType()).deserialize(""), elName);
        nnToXml(normalizedNodeContext, "<" + elName + "/>");
    }

    @Test
    public void nnAsYangBooleanToXmlTest() throws Exception {
        final String elName = "lfBoolean";
        NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(BaseTypes.booleanType()).deserialize("false"), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">false</" + elName + ">");

        normalizedNodeContext = prepareNNC(TypeDefinitionAwareCodec.from(BaseTypes.booleanType()).deserialize("true"),
                elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">true</" + elName + ">");
    }

    @Test
    public void nnAsYangUnionToXmlTest() throws Exception {
        final BitsTypeDefinition.Bit mockBit1 = Mockito.mock(BitsTypeDefinition.Bit.class);
        Mockito.when(mockBit1.getName()).thenReturn("first");
        Mockito.when(mockBit1.getPosition()).thenReturn(Uint32.ONE);
        final BitsTypeDefinition.Bit mockBit2 = Mockito.mock(BitsTypeDefinition.Bit.class);
        Mockito.when(mockBit2.getName()).thenReturn("second");
        Mockito.when(mockBit2.getPosition()).thenReturn(Uint32.TWO);

        final BitsTypeBuilder bitsTypeBuilder = BaseTypes.bitsTypeBuilder(QName.create("foo", "foo"));
        bitsTypeBuilder.addBit(mockBit1);
        bitsTypeBuilder.addBit(mockBit2);

        final UnionTypeBuilder unionTypeBuilder = BaseTypes.unionTypeBuilder(QName.create("foo", "foo"));
        unionTypeBuilder.addType(BaseTypes.int8Type());
        unionTypeBuilder.addType(bitsTypeBuilder.build());
        unionTypeBuilder.addType(BaseTypes.booleanType());
        unionTypeBuilder.addType(BaseTypes.stringType());

        final String elName = "lfUnion";

        // test int8
        final String int8 = "15";
        NormalizedNodeContext normalizedNodeContext = prepareNNC(
                TypeDefinitionAwareCodec.from(unionTypeBuilder.build()).deserialize(int8), elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">15</" + elName + ">");

        // test bits
        final String bits = "first second";
        normalizedNodeContext = prepareNNC(TypeDefinitionAwareCodec.from(unionTypeBuilder.build()).deserialize(bits),
                elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">[first, second]</" + elName + ">");

        // test boolean
        final String bool = "true";
        normalizedNodeContext = prepareNNC(TypeDefinitionAwareCodec.from(unionTypeBuilder.build()).deserialize(bool),
                elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">true</" + elName + ">");

        // test string
        final String s = "Hi!";
        normalizedNodeContext = prepareNNC(TypeDefinitionAwareCodec.from(unionTypeBuilder.build()).deserialize(s),
                elName);
        nnToXml(normalizedNodeContext, "<" + elName + ">Hi!</" + elName + ">");
    }

    private static NormalizedNodeContext prepareNNC(final Object object, final String name) {
        final QName cont = QName.create("basic:module", "2013-12-02", "cont");
        final QName lf = QName.create("basic:module", "2013-12-02", name);

        final DataSchemaNode contSchema = schemaContext.getDataChildByName(cont);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> contData = Builders
                .containerBuilder((ContainerSchemaNode) contSchema);

        final List<DataSchemaNode> instanceLf = ControllerContext
                .findInstanceDataChildrenByName((DataNodeContainer) contSchema, lf.getLocalName());
        final DataSchemaNode schemaLf = Iterables.getFirst(instanceLf, null);

        contData.withChild(Builders.leafBuilder((LeafSchemaNode) schemaLf).withValue(object).build());

        final NormalizedNodeContext testNormalizedNodeContext = new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, contSchema, null, schemaContext), contData.build());

        return testNormalizedNodeContext;
    }

    private void nnToXml(final NormalizedNodeContext normalizedNodeContext, final String... xmlRepresentation)
            throws Exception {
        final OutputStream output = new ByteArrayOutputStream();
        this.xmlBodyWriter.writeTo(normalizedNodeContext, null, null, null, this.mediaType, null, output);

        for (String element : xmlRepresentation) {
            assertTrue(output.toString().contains(element));
        }
    }

    private static NormalizedNodeContext prepareLeafrefData() {
        final QName cont = QName.create("basic:module", "2013-12-02", "cont");

        return new NormalizedNodeContext(
            new InstanceIdentifierContext<>(null, schemaContext.getDataChildByName(cont), null, schemaContext),
            Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(cont))
                .withChild(
                    ImmutableNodes.leafNode(QName.create("basic:module", "2013-12-02", "lfBoolean"), Boolean.TRUE))
                .withChild(
                    ImmutableNodes.leafNode(QName.create("basic:module", "2013-12-02", "lfLfref"), "true"))
                .build());
    }

    private static NormalizedNodeContext prepareLeafrefNegativeData() {
        final QName cont = QName.create("basic:module", "2013-12-02", "cont");

        return new NormalizedNodeContext(
            new InstanceIdentifierContext<>(null, schemaContext.getDataChildByName(cont), null, schemaContext),
            Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(cont))
                .withChild(ImmutableNodes.leafNode(
                    QName.create("basic:module", "2013-12-02", "lfLfrefNegative"), "value"))
                .build());
    }

    private static NormalizedNodeContext prepareIdrefData(final String prefix, final boolean valueAsQName) {
        final QName cont = QName.create("basic:module", "2013-12-02", "cont");

        Object value = null;
        if (valueAsQName) {
            value = QName.create("referenced:module", "2013-12-02", "iden");
        } else {
            value = "no qname value";
        }

        return new NormalizedNodeContext(
            new InstanceIdentifierContext<>(null, schemaContext.getDataChildByName(cont), null, schemaContext),
            Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(cont))
                .withChild(Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(QName.create("basic:module", "2013-12-02", "cont1")))
                    .withChild(ImmutableNodes.leafNode(QName.create("basic:module", "2013-12-02", "lf11"), value))
                    .build())
                .build());
    }

    @Override
    protected MediaType getMediaType() {
        return null;
    }
}
