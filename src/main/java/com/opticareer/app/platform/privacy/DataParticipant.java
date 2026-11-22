package com.opticareer.app.platform.privacy;

import java.util.UUID;

public interface DataParticipant {
  String module();

  Object exportData(UUID owner);

  void eraseData(UUID owner);
}
