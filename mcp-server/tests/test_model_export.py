import unittest
from unittest.mock import AsyncMock,patch
from cameo_mcp import client


class NativeExportClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_generic_resource_transport_does_not_interpret_content(self):
        result={"schemaVersion":1,"exportId":"capture","files":[]}
        with patch.object(client,"_request",AsyncMock(return_value=result)) as call:
            self.assertEqual(await client.model_export("open",{"projectId":"session","rootIds":["p"]}),result)
            call.assert_awaited_once_with("POST","/model-exports/open",json_body={"schemaVersion":1,"projectId":"session","rootIds":["p"]},timeout=35)
            with self.assertRaises(ValueError):await client.model_export("arbitrary",{})
