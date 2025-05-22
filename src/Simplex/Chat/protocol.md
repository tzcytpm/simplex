# Chat protocol

## Design constraints

- the transport message has a fixed size (8 or 16kb), but the SMP agent will be updated to support sending messages up to maximum configured size (TBC - 64-256kb) in 8-16Kb blocks.
- the chat message can have multiple content parts, but it should fit the agent message of the variable size.
- one of the chat message types should support transmitting large binaries in chunks that could potentially be interleaved with other messages. For example, image preview would fit the message, but the full size image will be transmitted in chunks later - same for large files.
- using object storage can be effective for large groups, but we will postpone it until content channels are implemented.

## Questions

- should content types be:
  - limited to MIME-types
  - separate content types vocabulary
  - both MIME types and extensions (currently we support MIME (m.) and Simplex (x.) namespaces)
  - allow additional content types namespaces

## Message syntax

The syntax of the message inside agent MSG:

```abnf
agentMessageBody = [chatMsgId] SP msgEvent SP [parameters] SP [contentParts [SP msgBodyParts]]
chatMsgId = 1*DIGIT ; used to refer to previous message;
msgEvent = protocolNamespace 1*("." msgTypeName)
protocolNamespace = 1*ALPHA ; "x" for all events defined in the protocol
msgTypeName = 1*ALPHA
parameters = parameter *("," parameter)
parameter = 1*(%x21-2B / %x2D-7E) ; exclude control characters, space, comma (%x2C)
contentParts = contentPart *("," contentPart)
contentPart = contentTypeNamespace "." contentType ":" contentSize [":" contentHash]
contentType = "i." <mime-type> / contentTypeNamespace "." 1*("." contentTypeName)
contentTypeNamespace = 1*ALPHA
contentTypeName = 1*ALPHA
contentHash = <base64>
msgBodyParts = msgBodyPart *(SP msgBodyPart)
msgEventParents = msgEventParent *msgEventParent ; binary body part for content type "x.dag"
msgEventParent = memberId refMsgId refMsgHash
memberId = 8*8(OCTET) ; shared member ID
refMsgId = 8*8(OCTET) ; sequential message number - external agent message ID
refMsgHash = 16*16(OCTET) ; SHA256 of agent message body
```

### Example: messages, updates, groups

```
"3 x.msg.new c.text x.text:5 hello "
"4 x.msg.new c.image i.image/jpg:256,i.image/png:4096 abcd abcd "
"4 x.msg.new c.image x.dag:32,i.image/jpg:8000,i.image/png:16000 binary1"
"5 x.msg.new c.image,i.image/jpg:150000 i.image/jpg:256 abcd "
"6 x.msg.file 5,1.1 x.file:60000 abcd "
"7 x.msg.file 5,1.2 x.file:60000 abcd "
"8 x.msg.file 5,1.3 x.file:30000 abcd "
'8 x.msg.update 3 x.text:11,x.dag:16 hello there abcd '
'9 x.msg.delete 3'
'10 x.msg.new app/v1 i.text/html:NNN,i.text/css:NNN,c.js:NNN,c.json:NNN ... ... ... {...} '
'11 x.msg.eval 8 c.json:NNN {...} '
'12 x.msg.new c.text x.text:16,x.dag:32 hello there @123 abcd '
' x.grp.mem.inv 23456,123 x.json:NNN {...} '
' x.grp.mem.acpt 23456 x.text:NNN <invitation> '
' x.grp.mem.intro 23456,234 x.json:NNN {...} '
' x.grp.mem.inv 23456,234 x.text:NNN <invitation> '
' x.grp.mem.req 23456,123 x.json:NNN {...} '
' x.grp.mem.direct.inv 23456,234 x.text:NNN <invitation>  '
' x.file name,size x.text:NNN <invitation> '
```

Chat message JTD:

```jsonc
{
  "properties": {
    "msgId": {"type": "string"},
    "minVersion": {"type": "uint16"}, // Word16
    "maxVersion": {"type": "uint16"}, // Word16
    "event": {"type": "string"}, // Text e.g. s.ok
    "params": {"values": {}}, // Map Text Value
  },
  "optionalProperties": {
    "dag": {"type": "string"}
  }
}
```

Events:

```jsonc
"event": "x.msg.new" // XMsgNew
"params":            // MsgContent
{
  "content": {
    "msgType": "text",
    // field "files" can be represented in content as contentType "file" with length prepended or as complex contentData
    "text": "<msg text>"
  }
  // "content": [
    // free form contentType for extensibility and/or complex content types? e.g. MIME
    // could it be useful if contentData was free form as well? currently it is ByteString
  //  {"contentType": <content type>, "contentData": "<content data>"},
  //  ...
  //  {"contentType": <content type N>, "contentData": "<content data N>"}
  // ]
}

"event": "x.file" // XFile; TODO rename into x.file.inv?
"params":         // FileInvitation
{
  "file": {
    "fileName": "<file name>",
    "fileSize": <file size>, // integer
    "fileConnReq": "<file conn req>"
  }
}

"event": "x.file.acpt" // XFileAcpt
"params":              // String
{
  "fileName": "<file name>"
}

"event": "x.info" // XInfo
"params":         // Profile
{
  "profile": {
    "displayName": "<display name>",
    "fullName": "<full name>"
  }
}

"event": "x.contact" // XContact
"params":        // Profile (Maybe MsgContent)
{
  "profile": {
    "displayName": "<display name>",
    "fullName": "<full name>"
  },
  "content": {
    "msgType": "text",
    "text": "<msg text>"
  } // optional
}

"event": "x.grp.inv" // XGrpInv
"params":            // GroupInvitation
{
  "groupInvitation": {
    "fromMember": {
      "memberId": "<from_member ID>",
      "memberRole": "<from_member role>"
    },
    "invitedMember": {
      "memberId": "<invited_member ID>",
      "memberRole": "<invited_member role>"
    },
    "connRequest": "<conn request>",
    "groupProfile": {
      "displayName": "<display name>",
      "fullName": "<full name>"
    }
  }
}

"event": "x.grp.acpt" // XGrpAcpt
"params":             // MemberId
{
  "memberId": "<member ID>"
}

"event": "x.grp.mem.new" // XGrpMemNew
"params":                // MemberInfo
{
  "memberInfo": {
    "memberId": "<member ID>",
    "memberRole": "<member role>",
    "profile": {
      "displayName": "<display name>",
      "fullName": "<full name>"
    }
  }
}

"event": "x.grp.mem.intro" // XGrpMemIntro
"params":                  // MemberInfo
{
  "memberInfo": {
    "memberId": "<member ID>",
    "memberRole": "<member role>",
    "profile": {
      "displayName": "<display name>",
      "fullName": "<full name>"
    }
  }
}

"event": "x.grp.mem.inv" // XGrpMemInv
"params":                // MemberId IntroInvitation
{
  "memberId": "<member ID>",
  "memberIntro": {
    "groupConnReq": "<group conn req>",
    "directConnReq": "<direct conn req>"
  }
}

"event": "x.grp.mem.fwd" // XGrpMemFwd
"params":                // MemberInfo IntroInvitation
{
  "memberInfo": {
    "memberId": "<member ID>",
    "memberRole": "<member role>",
    "profile": {
      "displayName": "<display name>",
      "fullName": "<full name>"
    },
  },
  "memberIntro": {
    "groupConnReq": "<group conn req>",
    "directConnReq": "<direct conn req>"
  }
}

"event": "x.grp.mem.info" // XGrpMemInfo
"params":                 // MemberId Profile
{
  "memberId": "<member ID>",
  "profile": {
    "displayName": "<display name>",
    "fullName": "<full name>"
  }
}

"event": "x.grp.mem.con" // XGrpMemCon
"params":                // MemberId
{
  "memberId": "<member ID>"
}

"event": "x.grp.mem.con.all" // XGrpMemConAll
"params":                    // MemberId
{
  "memberId": "<member ID>"
}

"event": "x.grp.mem.del" // XGrpMemDel
"params":                // MemberId
{
  "memberId": "<member ID>"
}

"event": "x.grp.leave" // XGrpLeave
"params":
{}

"event": "x.grp.del" // XGrpDel
"params":
{}

"event": "x.info.probe" // XInfoProbe
"params":               // ByteString
{
  "probe": "<probe>"
}

"event": "x.info.probe.check" // XInfoProbeCheck
"params":                     // ByteString
{
  "probeHash": "<probe hash>"
}

"event": "x.info.probe.ok" // XInfoProbeOk
"params":                  // ByteString
{
  "probe": "<probe>"
}

"event": "x.ok" // XOk
"params":
{}
```

### Group protocol

#### Add group member

A -> B: invite to group - `MSG: x.grp.inv G_MEM_ID_A,G_MEM_ROLE_A,G_MEM_ID_B,G_MEM_ROLE_B,<invitation> x.json:NNN <group_profile>`
user B confirms
B -> A: establish group connection (B: JOIN, A: LET)
B -> Ag: join group - `in SMP confirmation: x.grp.acpt G_MEM_ID_B`
A -> group (including B)): announce group member: `MSG: N x.grp.mem.new G_MEM_ID_B,G_MEM_ROLE_B,G_MEM_ID_M,... x.json:NNN <B_profile>`

In the message `x.grp.mem.new` A sends the sorted list of all members to whom A is connected followed by the new member ID, role and profile. The following introductions will be sent about/to all members A "knows about" (includes members introduced to A and members who accepted group invitation but not connected yet), once they are connected, so it can be a bigger list than sent in `x.grp.mem.new`.

All members who received `x.grp.mem.new` from A should check the list of connected members and if any connected members that recipients invited to the group are not in this list, they should introduce them to this new member (the last ID, role and profile in `x.grp.mem.new`). That might lead to double introductions that would provide a stronger consistency of group membership at a cost of extra connection between some members that will be unused.

subsequent messages between A and B are via group connection
A -> Bg: intro member - `MSG: x.grp.mem.intro G_MEM_ID_M,G_MEM_ROLE_M x.json:NNN <M_profile>`
B -> Ag: inv for mem - `MSG: x.grp.mem.inv G_MEM_ID_M,<gr_invitation>,<dm_invitation>,<probe>`
M is an existing member, messages are via group connection
A -> Mg: fwd inv - `MSG: x.grp.mem.fwd G_MEM_ID_B,<gr_invitation>,<dm_invitation>,<probe>`
M -> Bg: establish group connection (M: JOIN, B: LET)
M -> B: establish direct connection (M: JOIN, B: LET)
M -> Bg: confirm profile and role - `CONF: x.grp.mem.info G_MEM_ID_M,G_MEM_ROLE x.json:NNN <M_profile>`
B -> Mg: send profile probe - `MSG: x.info.probe <probe>` - it should always be send, even when there is no profile match.
if M is a known contact (profile match) send probe to M:
  B -> M (via old DM conn): profile match probe: `MSG: x.info.probe.check <probe_hash>`
  M -> B (via old DM conn): probe confirm: `MSG: x.info.probe.ok <probe>`
  link to the same contact
B -> Ag: connected to M: `MSG: x.grp.mem.con G_MEM_ID_M`
M -> Ag: connected to M: `MSG: x.grp.mem.con G_MEM_ID_B`

once all members connected
A -> group: `MSG: N x.grp.mem.con.all G_MEM_ID_B`

#### Send group message

Example:

`MSG: N x.msg.new c.text x.text:5 hello `

#### Group member statuses

1. Me
  - invited
  - accepted
  - connected to member who invited me
  - announced to group
    - x.grp.mem.new to group
  - confirmed as connected to group
    - this happens once member who invited me sends x.grp.mem.ok to group
1. Member that I invited:
  - invited
  - accepted
  - connected to me
  - announced to group
    - this happens after x.grp.mem.new but before introductions are sent.
    This message is used to determine which members should be additionally introduced if they were announced before (or in "parallel").
  - confirmed as connected to group
2. Member who invited me
  - invited_me
  - connected to me
    - I won't know whether this member was announced or confirmed to group - with the correctly functioning clients it must have happened.
3. Prior member introduced to me after I joined (x.grp.mem.intro)
  - introduced
  - sent invitation
  - connected
  - connected directly (or confirmed existing contact)
4. Member I was introduced to after that member joined (via x.grp.mem.fwd)
  - announced via x.grp.mem.new
  - received invitation
  - connected
  - connected directly (or confirmed existing contact)

#### Introductions

1. Introductions I sent to members I invited
  - the time of joining is determined by the time of creating the connection and sending the x.grp.mem.new message to the group.
  - introductions of the members who were connected before the new member should be sent - how to determine which members were connected before?
    - use time stamp of creating connection, possibly in the member record - not very reliable, as time can change.
    - use record ID - requires changing the schema, as currently members are added as invited, not as connected. So possibly invited members should be tracked in a separate table, and all members should still be tracked together to ensure that memberId is unique.
    - record ID is also not 100% sufficient, as there can be forks in message history and I may need to intro the member I invited to the member that was announced after my member in my chronology, but in another graph branch.
    - some other mechanism that allows to establish who should be connected to whom and whether I should introduce or another member (in case of forks - although maybe we both can introduce and eventually two group connections will be created between these members and they would just ignore the first one - although in cases of multiple branches in the graph it can be N connections).
    - introductions/member connection statuses:
      - created introduction
      - sent to the member I invited
      - received the invitation from the member I invited
      - forwarded this invitation to previously connected member
      - received confirmation from member I invited
      - received confirmation from member I forwarded to
      - completed introduction and recorded that these members are now fully connected to each other
2. Introductions I received from the member who invited me
  - if somebody else sends such introduction - this is an error (can be logged or ignored)
  - duplicate memberId is an error (e.g. it is a member that was announced in the group broadcast - I should be introduced to this member, and not the other way around? Although it can happen in case of fork and maybe I should establish the connection anyway).
  - member connection status in this case is just a member status from part 3, so maybe no need to track invitations separately and just put SMPQueueInfo on member record.
3. Invitation forwarded to me by any prior member
  - any admin/owner can add members, so they can forward their queue invitations - I should just check forwarding member permission
  - duplicate memberId is an error
  - unannounced memberId is an error - I should have seen member announcement prior to receiving this forwarded invitation. Fork would not happen here as it is the same member that announces and forwards the invitation, so they should be in order.
  - member connection status in this case is just a member status from part 4, so maybe no need to track invitations separately and just put SMPQueueInfo on member record.
