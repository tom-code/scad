package diameter

object DiameterDictionary {
  final val AVP_VENDOR_SPECIFIC_APP_ID  = 260
  final val AVP_ORIGIN_HOST             = 264
  final val AVP_ORIGIN_REALM            = 296
  final val AVP_SESSION_ID              = 263
  final val AVP_VENDOR_ID               = 266
  final val AVP_RESULT_CODE             = 268
  final val AVP_AUTH_SESSION_STATE      = 277

  final val CMD_DWR   = 280
  final val CMD_CER   = 257
  final val CMD_UDR   = 306

  final val APP_SH  = 0x01000001

  final val VENDOR_3GPP = 10415
  final val AVP_DATA_REF        = 703
  final val AVP_USER_IDENTITY   = 700
  final val AVP_PUBLIC_IDENTITY = 601

  final val STATUS_SUCCESS = 2001
}
