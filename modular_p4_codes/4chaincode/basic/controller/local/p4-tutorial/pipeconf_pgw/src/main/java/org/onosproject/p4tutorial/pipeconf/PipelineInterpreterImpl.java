/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.p4tutorial.pipeconf;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions.OutputInstruction;
import org.onosproject.net.packet.DefaultInboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiControlMetadataId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiControlMetadata;
import org.onosproject.net.pi.runtime.PiPacketOperation;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.onlab.util.ImmutableByteSequence.copyFrom;
import static org.onosproject.net.PortNumber.CONTROLLER;
import static org.onosproject.net.PortNumber.FLOOD;
import static org.onosproject.net.flow.instructions.Instruction.Type.OUTPUT;
import static org.onosproject.net.pi.model.PiPacketOperationType.PACKET_OUT;
import static org.slf4j.LoggerFactory.getLogger;
import org.slf4j.Logger;

/**
 * Implementation of a pipeline interpreter for the mytunnel.p4 program.
 */
public final class PipelineInterpreterImpl
        extends AbstractHandlerBehaviour
        implements PiPipelineInterpreter {

    private static final Logger log = getLogger(PipelineInterpreterImpl.class);

    private static final String DOT = ".";
    private static final String HDR = "hdr";
    private static final String C_INGRESS = "c_ingress";

    private static final String C_EGRESS = "c_egress";
    private static final String T_L3_FWD = "t_l3_fwd";
    private static final String T_S1_UPLINK = "ip_op_tun_s1_uplink";
    //private static final String T_S2_UPLINK = "ip_op_tun_s2_uplink";
    private static final String T_S3_UPLINK = "tun_egress_s3_uplink";

    //private static final String T_S1_DOWNLINK = "tun_egress_s1_downlink";
    //private static final String T_S2_DOWNLINK = "ip_op_tun_s2_downlink";
    //private static final String T_S3_DOWNLINK = "ip_op_tun_s3_downlink";

    private static final String T_uekey_uestate = "uekey_uestate_map";
    private static final String T_service_req_uekey_sgwteid_map = "service_req_uekey_sgwteid_map";
    private static final String T_ctxt_setup_uekey_sgwteid_map = "ctxt_setup_uekey_sgwteid_map";
    private static final String T_ctxt_release_uekey_sgwteid_map = "ctxt_release_uekey_sgwteid_map";
    private static final String T_uekey_guti = "uekey_guti_map";

    
    private static final String EGRESS_PORT = "egress_port";
    private static final String INGRESS_PORT = "ingress_port";
    private static final String ETHERNET = "ethernet";
    private static final String VLAN = "vlan";
    private static final String IPV4 = "ipv4";
    private static final String UDP = "udp";
    private static final String DATA = "data";

    private static final String STANDARD_METADATA = "standard_metadata";
    private static final int PORT_FIELD_BITWIDTH = 9;

    private static final PiMatchFieldId INGRESS_PORT_ID =
            PiMatchFieldId.of(STANDARD_METADATA + DOT + "ingress_port");
    private static final PiMatchFieldId INGRESS_VLAN_ID =
            PiMatchFieldId.of(HDR + DOT + VLAN + DOT + "vid");
    private static final PiMatchFieldId ETH_DST_ID =
            PiMatchFieldId.of(HDR + DOT + ETHERNET + DOT + "dstAddr");
    private static final PiMatchFieldId ETH_SRC_ID =
            PiMatchFieldId.of(HDR + DOT + ETHERNET + DOT + "srcAddr");
    private static final PiMatchFieldId IPV4_SRC_ID =
            PiMatchFieldId.of(HDR + DOT + IPV4 + DOT + "srcAddr");
    private static final PiMatchFieldId IPV4_DST_ID =
            PiMatchFieldId.of(HDR + DOT + IPV4 + DOT + "dstAddr");
    private static final PiMatchFieldId UDP_DST_PORT_ID =
           PiMatchFieldId.of(HDR + DOT + UDP + DOT + "dstPort");
    private static final PiMatchFieldId ETH_TYPE_ID =
            PiMatchFieldId.of(HDR + DOT + ETHERNET + DOT + "etherType");
    private static final PiMatchFieldId EPC_CODE =
            PiMatchFieldId.of(HDR + DOT + DATA + DOT + "epc_traffic_code");
    

    private static final PiTableId TABLE_L3_FWD_ID =
            PiTableId.of(C_INGRESS + DOT + T_L3_FWD);
    private static final PiTableId TABLE_S1_UPLINK =
            PiTableId.of(C_INGRESS + DOT + T_S1_UPLINK);
    //private static final PiTableId TABLE_S2_UPLINK =
      //      PiTableId.of(C_INGRESS + DOT + T_S2_UPLINK);
    private static final PiTableId TABLE_S3_UPLINK =
            PiTableId.of(C_INGRESS + DOT + T_S3_UPLINK);

    //private static final PiTableId TABLE_S1_DOWNLINK =
      //      PiTableId.of(C_INGRESS + DOT + T_S1_DOWNLINK);
    //private static final PiTableId TABLE_S2_DOWNLINK =
        //    PiTableId.of(C_INGRESS + DOT + T_S2_DOWNLINK);
    //private static final PiTableId TABLE_S3_DOWNLINK =
      //      PiTableId.of(C_INGRESS + DOT + T_S3_DOWNLINK);

            // @offload tables
        /*private static final PiTableId TABLE_uekey_uestate =
            PiTableId.of(C_INGRESS + DOT + T_uekey_uestate);
        private static final PiTableId TABLE_service_req_uekey_sgwteid_map =
            PiTableId.of(C_INGRESS + DOT + T_service_req_uekey_sgwteid_map);
        private static final PiTableId TABLE_ctxt_setup_uekey_sgwteid_map =
            PiTableId.of(C_INGRESS + DOT + T_ctxt_setup_uekey_sgwteid_map);
        private static final PiTableId TABLE_ctxt_release_uekey_sgwteid_map =
            PiTableId.of(C_EGRESS + DOT + T_ctxt_release_uekey_sgwteid_map);
     
        private static final PiTableId TABLE_uekey_guti =
            PiTableId.of(C_INGRESS + DOT + T_uekey_guti);
     */

    private static final PiActionId ACT_ID_NOP =
            PiActionId.of("NoAction");
    private static final PiActionId ACT_ID_SEND_TO_CPU =
            PiActionId.of(C_INGRESS + DOT + "send_to_cpu");
    private static final PiActionId ACT_ID_IPV4_FORWARD =
            PiActionId.of(C_INGRESS + DOT + "ipv4_forward");
    private static final PiActionId ACT_ID_POPULATE_S1_UPLINK =
            PiActionId.of(C_INGRESS + DOT + "populate_ip_op_tun_s1_uplink"); 
    private static final PiActionId ACT_ID_POPULATE_S2_UPLINK =
            PiActionId.of(C_INGRESS + DOT + "populate_ip_op_tun_s2_uplink");
    private static final PiActionId ACT_ID_POPULATE_S3_UPLINK =
            PiActionId.of(C_INGRESS + DOT + "populate_tun_egress_s3_uplink");

    private static final PiActionId ACT_ID_POPULATE_S1_DOWNLINK =
            PiActionId.of(C_INGRESS + DOT + "populate_tun_egress_s1_downlink"); 
    private static final PiActionId ACT_ID_POPULATE_S2_DOWNLINK =
            PiActionId.of(C_INGRESS + DOT + "populate_ip_op_tun_s2_downlink"); 
    private static final PiActionId ACT_ID_POPULATE_S3_DOWNLINK =
            PiActionId.of(C_INGRESS + DOT + "populate_ip_op_tun_s3_downlink"); 
        
            // @offload action
            private static final PiActionId ACT_ID_POPULATE_uekey_uestate_map =
            PiActionId.of(C_INGRESS + DOT + "populate_uekey_uestate_map"); 
            private static final PiActionId ACT_ID_POPULATE_service_req_uekey_sgwteid_map =
            PiActionId.of(C_INGRESS + DOT + "populate_service_req_uekey_sgwteid_map"); 
            private static final PiActionId ACT_ID_POPULATE_ctxt_setup_uekey_sgwteid_map =
            PiActionId.of(C_INGRESS + DOT + "populate_ctxt_setup_uekey_sgwteid_map"); 
            private static final PiActionId ACT_ID_POPULATE_ctxt_release_uekey_sgwteid_map =
            PiActionId.of(C_EGRESS + DOT + "populate_ctxt_release_uekey_sgwteid_map"); 
            private static final PiActionId ACT_ID_POPULATE_uekey_guti_map =
            PiActionId.of(C_INGRESS + DOT + "populate_uekey_guti_map"); 
                     

    private static final PiActionParamId ACT_PARAM_ID_PORT =
            PiActionParamId.of("port");
    private static final PiActionParamId ACT_PARAM_ID_ETH_DST_ADDR =
            PiActionParamId.of("dstAddr");
    private static final PiActionParamId ACT_PARAM_ID_S1_EGRESS_PORT =
            PiActionParamId.of("egress_port_s1"); 
    private static final PiActionParamId ACT_PARAM_ID_S2_EGRESS_PORT =
            PiActionParamId.of("egress_port_s2"); 
    private static final PiActionParamId ACT_PARAM_ID_S3_EGRESS_PORT =
            PiActionParamId.of("egress_port_s3");  
    private static final PiActionParamId ACT_PARAM_ID_S1_OUT_TUNNEL =
            PiActionParamId.of("op_tunnel_s1");
    private static final PiActionParamId ACT_PARAM_ID_S2_OUT_TUNNEL =
            PiActionParamId.of("op_tunnel_s2");  
    private static final PiActionParamId ACT_PARAM_ID_S3_OUT_TUNNEL =
            PiActionParamId.of("op_tunnel_s3");       
            
        //     @offload
        private static final PiActionParamId ACT_PARAM_ID_uestate =
        PiActionParamId.of("uestate");
        private static final PiActionParamId ACT_PARAM_ID_sgwteid =
        PiActionParamId.of("sgwteid");
        private static final PiActionParamId ACT_PARAM_ID_S3_guti =
        PiActionParamId.of("guti");

    private static final BiMap<Integer, PiTableId> TABLE_MAP =
            new ImmutableBiMap.Builder<Integer, PiTableId>()
                    .put(0, TABLE_L3_FWD_ID)
                    .build();

    private static final BiMap<Criterion.Type, PiMatchFieldId> CRITERION_MAP =
            new ImmutableBiMap.Builder<Criterion.Type, PiMatchFieldId>()
                //     .put(Criterion.Type.IPV4_DST,IPV4_DST_ID)
                    .put(Criterion.Type.IN_PORT, INGRESS_PORT_ID)
                    .put(Criterion.Type.ETH_DST, ETH_DST_ID)
                    .put(Criterion.Type.ETH_SRC, ETH_SRC_ID)
                    .put(Criterion.Type.ETH_TYPE, ETH_TYPE_ID)
                    .build();

    @Override
    public Optional<PiMatchFieldId> mapCriterionType(Criterion.Type type) {
        return Optional.ofNullable(CRITERION_MAP.get(type));
    }

    @Override
    public Optional<Criterion.Type> mapPiMatchFieldId(PiMatchFieldId headerFieldId) {
        return Optional.ofNullable(CRITERION_MAP.inverse().get(headerFieldId));
    }

    @Override
    public Optional<PiTableId> mapFlowRuleTableId(int flowRuleTableId) {
        return Optional.ofNullable(TABLE_MAP.get(flowRuleTableId));
    }

    @Override
    public Optional<Integer> mapPiTableId(PiTableId piTableId) {
        return Optional.ofNullable(TABLE_MAP.inverse().get(piTableId));
    }

    @Override
    public PiAction mapTreatment(TrafficTreatment treatment, PiTableId piTableId)
            throws PiInterpreterException {

        if (piTableId != TABLE_L3_FWD_ID) {
            throw new PiInterpreterException(
                    "Can map treatments only for 't_l3_fwd' table");
        }

        if (treatment.allInstructions().size() == 0) {
            // 0 instructions means "NoAction"
            return PiAction.builder().withId(ACT_ID_NOP).build();
        } else if (treatment.allInstructions().size() > 1) {
            // We understand treatments with only 1 instruction.
            throw new PiInterpreterException("Treatment has multiple instructions");
        }

        // Get the first and only instruction.
        Instruction instruction = treatment.allInstructions().get(0);
        // log.warn("interpretor instruction = {}",instruction);

        if (instruction.type() != OUTPUT) {
            // We can map only instructions of type OUTPUT.
            throw new PiInterpreterException(format(
                    "Instruction of type '%s' not supported", instruction.type()));
        }

        OutputInstruction outInstruction = (OutputInstruction) instruction;
        PortNumber port = outInstruction.port();
        // log.warn("interpretor port  = {}",port.toLong());
        if (!port.isLogical()) {
                // log.warn("interpretor inside !port.isLogical()");
            return PiAction.builder()
                    .withId(ACT_ID_IPV4_FORWARD)
                    .withParameter(new PiActionParam(
                            ACT_PARAM_ID_PORT, copyFrom(port.toLong())))
                    .build();
        } else if (port.equals(CONTROLLER)) {
                // log.warn("interpretor inside port.equals(CONTROLLER)");

            return PiAction.builder()
                    .withId(ACT_ID_SEND_TO_CPU)
                    .build();
        } else {
            throw new PiInterpreterException(format(
                    "Output on logical port '%s' not supported", port));
        }
    }

    @Override
    public Collection<PiPacketOperation> mapOutboundPacket(OutboundPacket packet)
            throws PiInterpreterException {

        TrafficTreatment treatment = packet.treatment();

        // We support only packet-out with OUTPUT instructions.
        if (treatment.allInstructions().size() != 1 &&
                treatment.allInstructions().get(0).type() != OUTPUT) {
            throw new PiInterpreterException(
                    "Treatment not supported: " + treatment.toString());
        }

        Instruction instruction = treatment.allInstructions().get(0);
        PortNumber port = ((OutputInstruction) instruction).port();
        List<PiPacketOperation> piPacketOps = Lists.newArrayList();

        if (!port.isLogical()) {
            piPacketOps.add(createPiPacketOp(packet.data(), port.toLong()));
        } else if (port.equals(FLOOD)) {
            // Since mytunnel.p4 does not support flooding, we create a packet
            // operation for each switch port.
            DeviceService deviceService = handler().get(DeviceService.class);
            DeviceId deviceId = packet.sendThrough();
            for (Port p : deviceService.getPorts(deviceId)) {
                piPacketOps.add(createPiPacketOp(packet.data(), p.number().toLong()));
            }
        } else {
            throw new PiInterpreterException(format(
                    "Output on logical port '%s' not supported", port));
        }

        return piPacketOps;
    }

    @Override
    public InboundPacket mapInboundPacket(PiPacketOperation packetIn)
            throws PiInterpreterException {
        // We assume that the packet is ethernet, which is fine since mytunnel.p4
        // can deparse only ethernet packets.
        Ethernet ethPkt;

        try {
            ethPkt = Ethernet.deserializer().deserialize(
                    packetIn.data().asArray(), 0, packetIn.data().size());
        } catch (DeserializationException dex) {
            throw new PiInterpreterException(dex.getMessage());
        }

        // Returns the ingress port packet metadata.
        Optional<PiControlMetadata> packetMetadata = packetIn.metadatas().stream()
                .filter(metadata -> metadata.id().toString().equals(INGRESS_PORT))
                .findFirst();

        if (packetMetadata.isPresent()) {
            short s = packetMetadata.get().value().asReadOnlyBuffer().getShort();
            ConnectPoint receivedFrom = new ConnectPoint(
                    packetIn.deviceId(), PortNumber.portNumber(s));
            return new DefaultInboundPacket(
                    receivedFrom, ethPkt, packetIn.data().asReadOnlyBuffer());
        } else {
            throw new PiInterpreterException(format(
                    "Missing metadata '%s' in packet-in received from '%s': %s",
                    INGRESS_PORT, packetIn.deviceId(), packetIn));
        }
    }

    private PiPacketOperation createPiPacketOp(ByteBuffer data, long portNumber)
            throws PiInterpreterException {
        PiControlMetadata metadata = createControlMetadata(portNumber);
        return PiPacketOperation.builder()
                .forDevice(this.data().deviceId())
                .withType(PACKET_OUT)
                .withData(copyFrom(data))
                .withMetadatas(ImmutableList.of(metadata))
                .build();
    }

    private PiControlMetadata createControlMetadata(long portNumber)
            throws PiInterpreterException {
        try {
            return PiControlMetadata.builder()
                    .withId(PiControlMetadataId.of(EGRESS_PORT))
                    .withValue(copyFrom(portNumber).fit(PORT_FIELD_BITWIDTH))
                    .build();
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new PiInterpreterException(format(
                    "Port number %d too big, %s", portNumber, e.getMessage()));
        }
    }
}
