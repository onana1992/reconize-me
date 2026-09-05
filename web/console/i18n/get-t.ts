import { getLocale } from "./get-locale";
import { createT, getMessages, type Translate } from "./messages";

export async function getT(): Promise<Translate> {
  const locale = await getLocale();
  return createT(getMessages(locale));
}
