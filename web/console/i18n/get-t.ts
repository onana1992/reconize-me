import { cache } from "react";
import { getLocale } from "./get-locale";
import { createT, getMessages, type Translate } from "./messages";

export const getT = cache(async (): Promise<Translate> => {
  const locale = await getLocale();
  return createT(getMessages(locale));
});
